package com.exam.service;

import com.exam.dto.DashboardStatsVO;
import com.exam.mapper.ReservationMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ReservationMapper reservationMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Value("${app.dashboard.cache-ttl-minutes:10}")
    private int cacheTtlMinutes;

    public DashboardStatsVO getStats(String dimension, String dateStr, String monthStr) {
        String dim = normalizeDimension(dimension);
        String period = resolvePeriod(dim, dateStr, monthStr);
        String cacheKey = "dashboard:stats:" + dim + ":" + period;

        // 1. Try Redis cache
        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cachedJson)) {
                log.debug("Dashboard cache hit for key {}", cacheKey);
                return objectMapper.readValue(cachedJson, DashboardStatsVO.class);
            }
        } catch (Exception e) {
            log.warn("Redis read error for key {}: {}", cacheKey, e.getMessage());
        }

        // 2. Cache miss -> compute from DB
        DashboardStatsVO stats = computeFromDb(dim, period);

        // 3. Populate Redis cache
        try {
            String json = objectMapper.writeValueAsString(stats);
            stringRedisTemplate.opsForValue().set(cacheKey, json, cacheTtlMinutes, TimeUnit.MINUTES);
            log.debug("Populated dashboard cache for key {}", cacheKey);
        } catch (Exception e) {
            log.warn("Redis write error for key {}: {}", cacheKey, e.getMessage());
        }

        return stats;
    }

    public DashboardStatsVO refresh(String dimension, String dateStr, String monthStr) {
        String dim = normalizeDimension(dimension);
        String period = resolvePeriod(dim, dateStr, monthStr);
        String cacheKey = "dashboard:stats:" + dim + ":" + period;

        DashboardStatsVO stats = computeFromDb(dim, period);

        try {
            String json = objectMapper.writeValueAsString(stats);
            stringRedisTemplate.opsForValue().set(cacheKey, json, cacheTtlMinutes, TimeUnit.MINUTES);
            log.info("Forced refresh and updated dashboard cache for key {}", cacheKey);
        } catch (Exception e) {
            log.warn("Redis write error during refresh for key {}: {}", cacheKey, e.getMessage());
        }

        return stats;
    }

    private DashboardStatsVO computeFromDb(String dimension, String period) {
        List<DashboardStatsVO.RoomStat> roomStats;
        List<DashboardStatsVO.DeptStat> deptStats;

        if ("DATE".equalsIgnoreCase(dimension)) {
            LocalDate date = LocalDate.parse(period, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            roomStats = reservationMapper.findRoomStatsByDate(date);
            deptStats = reservationMapper.findDeptStatsByDate(date);
        } else {
            YearMonth ym = YearMonth.parse(period, DateTimeFormatter.ofPattern("yyyy-MM"));
            LocalDate startDate = ym.atDay(1);
            LocalDate endDate = ym.atEndOfMonth();
            roomStats = reservationMapper.findRoomStatsByMonth(startDate, endDate);
            deptStats = reservationMapper.findDeptStatsByMonth(startDate, endDate);
        }

        int totalBookings = roomStats.stream().mapToInt(DashboardStatsVO.RoomStat::getBookingCount).sum();
        long totalMinutes = roomStats.stream().mapToLong(DashboardStatsVO.RoomStat::getOccupiedMinutes).sum();
        int activeRooms = (int) roomStats.stream().filter(r -> r.getBookingCount() > 0).count();
        int activeDepts = (int) deptStats.stream().filter(d -> d.getBookingCount() > 0).count();

        DashboardStatsVO.Summary summary = DashboardStatsVO.Summary.builder()
                .totalBookings(totalBookings)
                .totalMinutes(totalMinutes)
                .activeRooms(activeRooms)
                .activeDepts(activeDepts)
                .build();

        return DashboardStatsVO.builder()
                .dimension(dimension)
                .period(period)
                .summary(summary)
                .roomStats(roomStats)
                .deptStats(deptStats)
                .build();
    }

    private String normalizeDimension(String dimension) {
        if ("MONTH".equalsIgnoreCase(dimension)) {
            return "MONTH";
        }
        return "DATE";
    }

    private String resolvePeriod(String dimension, String dateStr, String monthStr) {
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        if ("MONTH".equalsIgnoreCase(dimension)) {
            if (StringUtils.hasText(monthStr)) {
                return monthStr.trim();
            }
            return today.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        } else {
            if (StringUtils.hasText(dateStr)) {
                return dateStr.trim();
            }
            return today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
    }
}
