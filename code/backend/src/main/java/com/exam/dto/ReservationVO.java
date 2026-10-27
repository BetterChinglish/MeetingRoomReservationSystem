package com.exam.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationVO {
    private Long id;
    private Long roomId;
    private String roomName;
    private Long userId;
    private String userName;
    private Long deptId;
    private String deptName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate reserveDate;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime endTime;

    private String title;
    private String status; // 存储态
    private String computedStatus; // 即时业务态: PENDING, ACTIVE, ENDED, CANCELLED

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
