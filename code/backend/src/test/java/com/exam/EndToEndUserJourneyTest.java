package com.exam;

import com.exam.common.context.UserContext;
import com.exam.common.exception.BusinessException;
import com.exam.dto.DashboardStatsVO;
import com.exam.dto.PageResult;
import com.exam.dto.ReservationCreateRequest;
import com.exam.dto.ReservationVO;
import com.exam.entity.Reservation;
import com.exam.mapper.ReservationMapper;
import com.exam.service.DashboardService;
import com.exam.service.ReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class EndToEndUserJourneyTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private ReservationMapper reservationMapper;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    public void cleanup() {
        UserContext.clear();
    }

    @Test
    public void testEndToEndUserJourney() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
        when(ops.get(anyString())).thenReturn(null);

        // 1. Authenticate user context as emp001 (张伟, 研发部)
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(2L)
                .username("emp001")
                .name("张伟")
                .role("EMPLOYEE")
                .deptId(1L)
                .build());

        LocalDate futureDate = LocalDate.now().plusDays(2);

        // 2. Create reservation [14:00, 15:30) in Room 1
        ReservationCreateRequest createReq = new ReservationCreateRequest();
        createReq.setRoomId(1L);
        createReq.setReserveDate(futureDate);
        createReq.setStartTime(LocalTime.of(14, 0));
        createReq.setEndTime(LocalTime.of(15, 30));
        createReq.setDeptId(1L);
        createReq.setTitle("端到端全链路集成验证会议");

        ReservationVO createdVo = reservationService.createReservation(createReq);
        assertNotNull(createdVo.getId());
        assertEquals("PENDING", createdVo.getStatus());
        assertEquals("第一会议室", createdVo.getRoomName());

        // 3. Query list
        PageResult<ReservationVO> pageResult = reservationService.queryList(1L, futureDate, null, null, null, 1, 10);
        assertTrue(pageResult.getTotal() >= 1);
        boolean found = pageResult.getList().stream().anyMatch(r -> r.getId().equals(createdVo.getId()));
        assertTrue(found, "Newly created reservation must be present in the query result");

        // 4. View on dashboard
        DashboardStatsVO stats = dashboardService.getStats("DATE", futureDate.toString(), null);
        assertNotNull(stats);
        DashboardStatsVO.RoomStat room1Stat = stats.getRoomStats().stream()
                .filter(r -> r.getRoomId().equals(1L))
                .findFirst()
                .orElse(null);
        assertNotNull(room1Stat);
        assertTrue(room1Stat.getBookingCount() >= 1);
        assertTrue(room1Stat.getOccupiedMinutes() >= 90);

        // 5. Cancel reservation
        reservationService.cancel(createdVo.getId());
        Reservation cancelled = reservationMapper.findById(createdVo.getId());
        assertEquals("CANCELLED", cancelled.getStatus());

        // 6. Verify dashboard update
        DashboardStatsVO refreshedStats = dashboardService.refresh("DATE", futureDate.toString(), null);
        DashboardStatsVO.RoomStat refreshedRoom1Stat = refreshedStats.getRoomStats().stream()
                .filter(r -> r.getRoomId().equals(1L))
                .findFirst()
                .orElse(null);
        assertNotNull(refreshedRoom1Stat);
        assertEquals(0, refreshedRoom1Stat.getBookingCount(), "Cancelled booking must not be counted in dashboard");
    }

    @Test
    public void testConcurrentCollisionRejection() throws InterruptedException {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);

        LocalDate futureDate = LocalDate.now().plusDays(3);
        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    UserContext.set(UserContext.CurrentUser.builder()
                            .userId((long) (2 + index))
                            .username("user" + index)
                            .name("测试用户" + index)
                            .role("EMPLOYEE")
                            .deptId(1L)
                            .build());

                    ReservationCreateRequest req = new ReservationCreateRequest();
                    req.setRoomId(2L);
                    req.setReserveDate(futureDate);
                    req.setStartTime(LocalTime.of(16, 0));
                    req.setEndTime(LocalTime.of(17, 0));
                    req.setDeptId(1L);
                    req.setTitle("并发抢订会议 " + index);

                    reservationService.createReservation(req);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getCode() == 409) {
                        conflictCount.incrementAndGet();
                    } else {
                        errors.add(e);
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    UserContext.clear();
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one concurrent reservation should succeed");
        assertEquals(threads - 1, conflictCount.get(), "All other concurrent overlapping reservations must be rejected with 409 Conflict");
        assertTrue(errors.isEmpty(), "No unexpected errors should occur during concurrent booking");
    }
}
