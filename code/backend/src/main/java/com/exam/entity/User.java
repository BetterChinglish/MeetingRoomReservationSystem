package com.exam.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;
    private String username;
    private String password;
    private String name;
    private Long deptId;
    private String role; // EMPLOYEE, ADMIN
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
