package com.exam.controller;

import com.exam.common.result.ApiResponse;
import com.exam.dto.PageResult;
import com.exam.dto.ReservationCreateRequest;
import com.exam.dto.ReservationVO;
import com.exam.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ApiResponse<ReservationVO> create(@Valid @RequestBody ReservationCreateRequest request) {
        ReservationVO vo = reservationService.createReservation(request);
        return ApiResponse.created(vo);
    }

    @GetMapping
    public ApiResponse<PageResult<ReservationVO>> list(
            @RequestParam(value = "roomId", required = false) Long roomId,
            @RequestParam(value = "reserveDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate reserveDate,
            @RequestParam(value = "deptId", required = false) Long deptId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        PageResult<ReservationVO> result = reservationService.queryList(roomId, reserveDate, deptId, userId, status, page, size);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<ReservationVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(reservationService.getById(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable("id") Long id) {
        reservationService.cancel(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/early-end")
    public ApiResponse<Void> earlyEnd(@PathVariable("id") Long id) {
        reservationService.earlyEnd(id);
        return ApiResponse.success();
    }
}
