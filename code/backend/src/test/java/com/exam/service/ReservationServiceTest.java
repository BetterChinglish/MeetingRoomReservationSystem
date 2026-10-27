package com.exam.service;

import com.exam.common.context.UserContext;
import com.exam.common.exception.BusinessException;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReservationServiceTest {

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private MeetingRoomMapper meetingRoomMapper;

    @Mock
    private DepartmentMapper departmentMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private ReservationService reservationService;

    @BeforeEach
    public void setup() {
        ReflectionTestUtils.setField(reservationService, "submitLockSeconds", 5);
        lenient().when(transactionManager.getTransaction(any()))
                .thenReturn(mock(org.springframework.transaction.TransactionStatus.class));
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(2L)
                .username("emp001")
                .name("张伟")
                .role("EMPLOYEE")
                .deptId(1L)
                .build());
    }

    @AfterEach
    public void tearDown() {
        UserContext.clear();
    }

    @Test
    public void testCreateReservationAdjacentSlotSuccess() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        LocalDate futureDate = LocalDate.now().plusDays(1);
        ReservationCreateRequest req = new ReservationCreateRequest();
        req.setRoomId(1L);
        req.setReserveDate(futureDate);
        req.setStartTime(LocalTime.of(10, 0));
        req.setEndTime(LocalTime.of(11, 0));
        req.setDeptId(1L);
        req.setTitle("架构讨论会");

        MeetingRoom room = MeetingRoom.builder().id(1L).name("第一会议室").status("ENABLED").build();
        when(meetingRoomMapper.findById(1L)).thenReturn(room);

        Department dept = Department.builder().id(1L).name("研发部").build();
        when(departmentMapper.findById(1L)).thenReturn(dept);

        User user = User.builder().id(2L).name("张伟").build();
        when(userMapper.findById(2L)).thenReturn(user);

        // Existing reservation is adjacent: [09:00, 10:00)
        Reservation existing = Reservation.builder()
                .id(99L)
                .roomId(1L)
                .reserveDate(futureDate)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .status("PENDING")
                .build();
        when(reservationMapper.findActiveByRoomAndDateForUpdate(1L, futureDate))
                .thenReturn(List.of(existing));

        when(reservationMapper.insert(any(Reservation.class))).thenReturn(1);

        ReservationVO vo = reservationService.createReservation(req);
        assertNotNull(vo);
        assertEquals("第一会议室", vo.getRoomName());
        assertEquals("PENDING", vo.getStatus());
        verify(reservationMapper).insert(any(Reservation.class));
    }

    @Test
    public void testCreateReservationCollisionDetected() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        LocalDate futureDate = LocalDate.now().plusDays(1);
        ReservationCreateRequest req = new ReservationCreateRequest();
        req.setRoomId(1L);
        req.setReserveDate(futureDate);
        req.setStartTime(LocalTime.of(9, 30));
        req.setEndTime(LocalTime.of(10, 30));
        req.setDeptId(1L);
        req.setTitle("冲突会议");

        MeetingRoom room = MeetingRoom.builder().id(1L).name("第一会议室").status("ENABLED").build();
        when(meetingRoomMapper.findById(1L)).thenReturn(room);

        Department dept = Department.builder().id(1L).name("研发部").build();
        when(departmentMapper.findById(1L)).thenReturn(dept);

        User user = User.builder().id(2L).name("张伟").build();
        when(userMapper.findById(2L)).thenReturn(user);

        // Existing reservation overlaps: [09:00, 10:00)
        Reservation existing = Reservation.builder()
                .id(99L)
                .roomId(1L)
                .reserveDate(futureDate)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .title("已有早会")
                .userName("李雷")
                .status("PENDING")
                .build();
        when(reservationMapper.findActiveByRoomAndDateForUpdate(1L, futureDate))
                .thenReturn(List.of(existing));

        BusinessException ex = assertThrows(BusinessException.class, () -> reservationService.createReservation(req));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("时段冲突"));
    }

    @Test
    public void testCancelOtherUserReservationForbidden() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        Reservation record = Reservation.builder()
                .id(101L)
                .userId(3L) // Created by user 3, but current user is 2
                .reserveDate(futureDate)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .status("PENDING")
                .build();
        when(reservationMapper.findById(101L)).thenReturn(record);

        BusinessException ex = assertThrows(BusinessException.class, () -> reservationService.cancel(101L));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("无权限"));
    }
}
