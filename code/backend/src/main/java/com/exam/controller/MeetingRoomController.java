package com.exam.controller;

import com.exam.annotation.RequireAdmin;
import com.exam.common.result.ApiResponse;
import com.exam.entity.MeetingRoom;
import com.exam.service.MeetingRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class MeetingRoomController {

    private final MeetingRoomService meetingRoomService;

    @GetMapping("/active")
    public ApiResponse<List<MeetingRoom>> activeRooms() {
        return ApiResponse.success(meetingRoomService.getActiveRooms());
    }

    @GetMapping
    public ApiResponse<List<MeetingRoom>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.success(meetingRoomService.getAllRooms(status, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<MeetingRoom> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(meetingRoomService.getById(id));
    }

    @PostMapping
    @RequireAdmin
    public ApiResponse<MeetingRoom> create(@RequestBody MeetingRoom room) {
        return ApiResponse.created(meetingRoomService.create(room));
    }

    @PutMapping("/{id}")
    @RequireAdmin
    public ApiResponse<MeetingRoom> update(@PathVariable("id") Long id, @RequestBody MeetingRoom room) {
        room.setId(id);
        return ApiResponse.success(meetingRoomService.update(room));
    }

    @PutMapping("/{id}/status")
    @RequireAdmin
    public ApiResponse<MeetingRoom> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return ApiResponse.success(meetingRoomService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @RequireAdmin
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        meetingRoomService.delete(id);
        return ApiResponse.success();
    }
}
