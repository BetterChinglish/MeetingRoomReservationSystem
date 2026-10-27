package com.exam.service;

import com.exam.common.context.UserContext;
import com.exam.common.exception.BusinessException;
import com.exam.common.result.ResultCode;
import com.exam.dto.PageResult;
import com.exam.dto.ReservationCreateRequest;
import com.exam.dto.ReservationVO;
import com.exam.entity.Department;
import com.exam.entity.MeetingRoom;
import com.exam.entity.Reservation;
import com.exam.entity.User;
import com.exam.mapper.DepartmentMapper;
import com.exam.mapper.MeetingRoomMapper;
import com.exam.mapper.ReservationMapper;
import com.exam.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationMapper reservationMapper;
    private final MeetingRoomMapper meetingRoomMapper;
    private final DepartmentMapper departmentMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Value("${app.reservation.submit-lock-seconds:5}")
    private int submitLockSeconds;

    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.locks.ReentrantLock> roomDateLocks = new java.util.concurrent.ConcurrentHashMap<>();

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ReservationVO createReservation(ReservationCreateRequest request) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // 1. Validate start and end time
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException(400, "结束时间必须大于开始时间");
        }

        // 2. Reject past time
        LocalDateTime now = LocalDateTime.now(ZONE_SHANGHAI);
        LocalDateTime bookingStartDateTime = LocalDateTime.of(request.getReserveDate(), request.getStartTime());
        if (bookingStartDateTime.isBefore(now)) {
            throw new BusinessException(400, "不可预约过去时段");
        }

        // 3. Redis Idempotency submit lock
        String submitPayload = String.format("%d|%s|%s|%s|%s",
                request.getRoomId(),
                request.getReserveDate(),
                request.getStartTime(),
                request.getEndTime(),
                request.getTitle());
        String hash = DigestUtils.md5DigestAsHex(submitPayload.getBytes(StandardCharsets.UTF_8));
        String lockKey = "reservation:submit_lock:" + currentUserId + ":" + hash;

        Boolean lockAcquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", submitLockSeconds, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(lockAcquired)) {
            log.warn("User {} duplicate submit detected for key {}", currentUserId, lockKey);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS.getCode(), "请勿重复提交预约申请");
        }

        // 4. Validate meeting room status
        MeetingRoom room = meetingRoomMapper.findById(request.getRoomId());
        if (room == null || room.getDeletedAt() != null) {
            throw new BusinessException(404, "所选会议室不存在");
        }
        if (!"ENABLED".equalsIgnoreCase(room.getStatus())) {
            throw new BusinessException(400, "会议室已停用，无法发起新预约");
        }

        // 5. Validate department
        Department dept = departmentMapper.findById(request.getDeptId());
        if (dept == null || dept.getDeletedAt() != null) {
            throw new BusinessException(400, "所选部门不存在");
        }

        // 6. User snapshot
        User user = userMapper.findById(currentUserId);
        String userName = (user != null) ? user.getName() : "未知用户";

        String slotKey = request.getRoomId() + ":" + request.getReserveDate();
        java.util.concurrent.locks.ReentrantLock jvmLock = roomDateLocks.computeIfAbsent(slotKey, k -> new java.util.concurrent.locks.ReentrantLock(true));
        jvmLock.lock();
        try {
            // 7. Acquire exclusive row lock and check collision
            List<Reservation> existingList = reservationMapper.findActiveByRoomAndDateForUpdate(request.getRoomId(), request.getReserveDate());
            if (existingList.isEmpty()) {
                // Lock room row as fallback
                meetingRoomMapper.findByIdForUpdate(request.getRoomId());
            }

            // Collision formula: max(s1, s2) < min(e1, e2) <=> s_new < r.end_time AND r.start_time < e_new
            LocalTime sNew = request.getStartTime();
            LocalTime eNew = request.getEndTime();

            for (Reservation r : existingList) {
                LocalTime sExist = r.getStartTime();
                LocalTime eExist = r.getEndTime();

                if (sNew.isBefore(eExist) && sExist.isBefore(eNew)) {
                    log.info("Collision detected between [{}, {}) and [{}, {}) for room {}", sNew, eNew, sExist, eExist, request.getRoomId());
                    Map<String, Object> conflictDetails = new HashMap<>();
                    conflictDetails.put("conflictId", r.getId());
                    conflictDetails.put("title", r.getTitle());
                    conflictDetails.put("reserveDate", r.getReserveDate());
                    conflictDetails.put("startTime", r.getStartTime().toString());
                    conflictDetails.put("endTime", r.getEndTime().toString());
                    conflictDetails.put("userName", r.getUserName());
                    throw new BusinessException(ResultCode.CONFLICT.getCode(), "该时段已被预约，存在时段冲突", conflictDetails);
                }
            }

            // 8. Insert record
            Reservation reservation = Reservation.builder()
                    .roomId(room.getId())
                    .userId(currentUserId)
                    .reserveDate(request.getReserveDate())
                    .startTime(request.getStartTime())
                    .endTime(request.getEndTime())
                    .deptId(dept.getId())
                    .title(request.getTitle())
                    .roomName(room.getName())
                    .deptName(dept.getName())
                    .userName(userName)
                    .status("PENDING")
                    .build();

            reservationMapper.insert(reservation);

            // 9. Evict dashboard cache
            evictDashboardCache(request.getReserveDate());

            return toVO(reservation, now);
        } finally {
            jvmLock.unlock();
        }
    }
    }

    public PageResult<ReservationVO> queryList(Long roomId, LocalDate reserveDate, Long deptId, Long userId, String status, int page, int size) {
        UserContext.CurrentUser current = UserContext.get();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // Privilege rule: employee querying userId must be constrained to self
        Long effectiveUserId = userId;
        if (!current.isAdmin() && userId != null) {
            effectiveUserId = current.getUserId();
        }

        int pageSize = Math.min(Math.max(size, 1), 100);
        int pageNum = Math.max(page, 1);
        int offset = (pageNum - 1) * pageSize;

        long total = reservationMapper.countList(roomId, reserveDate, deptId, effectiveUserId, status);
        List<Reservation> list = reservationMapper.queryList(roomId, reserveDate, deptId, effectiveUserId, status, offset, pageSize);

        LocalDateTime now = LocalDateTime.now(ZONE_SHANGHAI);
        List<ReservationVO> voList = list.stream()
                .map(r -> toVO(r, now))
                .collect(Collectors.toList());

        return PageResult.<ReservationVO>builder()
                .total(total)
                .page(pageNum)
                .size(pageSize)
                .list(voList)
                .build();
    }

    public ReservationVO getById(Long id) {
        Reservation r = reservationMapper.findById(id);
        if (r == null) {
            throw new BusinessException(404, "预约记录不存在");
        }
        return toVO(r, LocalDateTime.now(ZONE_SHANGHAI));
    }

    @Transactional
    public void cancel(Long id) {
        UserContext.CurrentUser current = UserContext.get();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        Reservation r = reservationMapper.findById(id);
        if (r == null) {
            throw new BusinessException(404, "预约记录不存在");
        }

        LocalDateTime now = LocalDateTime.now(ZONE_SHANGHAI);
        String dynamicStatus = computeStatus(r, now);

        if (!"PENDING".equalsIgnoreCase(dynamicStatus)) {
            throw new BusinessException(400, "仅未开始的预约可取消");
        }

        // Permission: creator or admin
        boolean isCreator = r.getUserId().equals(current.getUserId());
        if (!isCreator && !current.isAdmin()) {
            throw new BusinessException(403, "无权限取消他人的预约");
        }

        reservationMapper.updateStatus(id, "CANCELLED", now);
        evictDashboardCache(r.getReserveDate());
    }

    @Transactional
    public void earlyEnd(Long id) {
        UserContext.CurrentUser current = UserContext.get();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        Reservation r = reservationMapper.findById(id);
        if (r == null) {
            throw new BusinessException(404, "预约记录不存在");
        }

        LocalDateTime now = LocalDateTime.now(ZONE_SHANGHAI);
        String dynamicStatus = computeStatus(r, now);

        if (!"ACTIVE".equalsIgnoreCase(dynamicStatus)) {
            throw new BusinessException(400, "仅已开始的预约可提前结束");
        }

        // Permission: creator or admin
        boolean isCreator = r.getUserId().equals(current.getUserId());
        if (!isCreator && !current.isAdmin()) {
            throw new BusinessException(403, "无权限提前结束他人的预约");
        }

        reservationMapper.updateStatus(id, "ENDED", now);
        evictDashboardCache(r.getReserveDate());
    }

    public String computeStatus(Reservation r, LocalDateTime now) {
        if ("CANCELLED".equalsIgnoreCase(r.getStatus())) {
            return "CANCELLED";
        }
        if ("ENDED".equalsIgnoreCase(r.getStatus())) {
            return "ENDED";
        }

        LocalDateTime start = LocalDateTime.of(r.getReserveDate(), r.getStartTime());
        LocalDateTime end = LocalDateTime.of(r.getReserveDate(), r.getEndTime());

        if (now.isBefore(start)) {
            return "PENDING";
        } else if (!now.isBefore(start) && now.isBefore(end)) {
            return "ACTIVE";
        } else {
            return "ENDED";
        }
    }

    private ReservationVO toVO(Reservation r, LocalDateTime now) {
        return ReservationVO.builder()
                .id(r.getId())
                .roomId(r.getRoomId())
                .roomName(r.getRoomName())
                .userId(r.getUserId())
                .userName(r.getUserName())
                .deptId(r.getDeptId())
                .deptName(r.getDeptName())
                .reserveDate(r.getReserveDate())
                .startTime(r.getStartTime())
                .endTime(r.getEndTime())
                .title(r.getTitle())
                .status(r.getStatus())
                .computedStatus(computeStatus(r, now))
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private void evictDashboardCache(LocalDate date) {
        try {
            String dateKey = "dashboard:stats:DATE:" + date.toString();
            String monthKey = "dashboard:stats:MONTH:" + date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            stringRedisTemplate.delete(List.of(dateKey, monthKey));
            log.info("Evicted dashboard cache keys: {} and {}", dateKey, monthKey);
        } catch (Exception e) {
            log.warn("Failed to evict dashboard cache: {}", e.getMessage());
        }
    }
}
