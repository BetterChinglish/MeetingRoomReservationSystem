package com.exam.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {
    private Long id;
    private Long roomId;
    private Long userId;
    private LocalDate reserveDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long deptId;
    private String title;
    private String roomName;
    private String deptName;
    private String userName;
    private String status; // PENDING, ACTIVE, ENDED, CANCELLED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
