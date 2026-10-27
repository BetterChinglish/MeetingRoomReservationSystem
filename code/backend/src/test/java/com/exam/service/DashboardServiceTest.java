package com.exam.service;

import com.exam.dto.DashboardStatsVO;
import com.exam.mapper.ReservationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DashboardServiceTest {

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private DashboardService dashboardService;

    @BeforeEach
    public void setup() {
        ReflectionTestUtils.setField(dashboardService, "cacheTtlMinutes", 10);
        ReflectionTestUtils.setField(dashboardService, "objectMapper", new ObjectMapper());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    public void testGetStatsCacheMissComputesFromDb() {
        when(valueOperations.get(anyString())).thenReturn(null);

        LocalDate date = LocalDate.of(2026, 10, 27);
        DashboardStatsVO.RoomStat r1 = DashboardStatsVO.RoomStat.builder().roomId(1L).roomName("第一会议室").bookingCount(2).occupiedMinutes(120).build();
        DashboardStatsVO.RoomStat r2 = DashboardStatsVO.RoomStat.builder().roomId(2L).roomName("第二会议室").bookingCount(0).occupiedMinutes(0).build();
        when(reservationMapper.findRoomStatsByDate(date)).thenReturn(List.of(r1, r2));

        DashboardStatsVO.DeptStat d1 = DashboardStatsVO.DeptStat.builder().deptId(1L).deptName("研发部").bookingCount(2).occupiedMinutes(120).build();
        when(reservationMapper.findDeptStatsByDate(date)).thenReturn(List.of(d1));

        DashboardStatsVO stats = dashboardService.getStats("DATE", "2026-10-27", null);

        assertNotNull(stats);
        assertEquals("DATE", stats.getDimension());
        assertEquals("2026-10-27", stats.getPeriod());
        assertEquals(2, stats.getSummary().getTotalBookings());
        assertEquals(120, stats.getSummary().getTotalMinutes());
        assertEquals(1, stats.getSummary().getActiveRooms());
        assertEquals(2, stats.getRoomStats().size());
        verify(valueOperations).set(eq("dashboard:stats:DATE:2026-10-27"), anyString(), eq(10L), any());
    }
}
