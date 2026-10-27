package com.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsVO implements Serializable {
    private String dimension; // DATE, MONTH
    private String period; // e.g. 2026-10-27 or 2026-10
    private Summary summary;
    private List<RoomStat> roomStats;
    private List<DeptStat> deptStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary implements Serializable {
        private int totalBookings;
        private long totalMinutes;
        private int activeRooms;
        private int activeDepts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomStat implements Serializable {
        private Long roomId;
        private String roomName;
        private int bookingCount;
        private long occupiedMinutes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeptStat implements Serializable {
        private Long deptId;
        private String deptName;
        private int bookingCount;
        private long occupiedMinutes;
    }
}
