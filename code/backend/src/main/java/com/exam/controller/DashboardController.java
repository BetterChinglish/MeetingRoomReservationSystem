package com.exam.controller;

import com.exam.common.result.ApiResponse;
import com.exam.dto.DashboardStatsVO;
import com.exam.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public ApiResponse<DashboardStatsVO> getStats(
            @RequestParam(value = "dimension", defaultValue = "DATE") String dimension,
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "month", required = false) String month) {
        return ApiResponse.success(dashboardService.getStats(dimension, date, month));
    }

    @PostMapping("/refresh")
    public ApiResponse<DashboardStatsVO> refresh(
            @RequestParam(value = "dimension", defaultValue = "DATE") String dimension,
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "month", required = false) String month) {
        return ApiResponse.success(dashboardService.refresh(dimension, date, month));
    }
}
