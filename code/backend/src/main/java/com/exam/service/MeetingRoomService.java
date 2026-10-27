package com.exam.service;

import com.exam.common.exception.BusinessException;
import com.exam.entity.MeetingRoom;
import com.exam.mapper.MeetingRoomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MeetingRoomService {

    private final MeetingRoomMapper meetingRoomMapper;

    public List<MeetingRoom> getActiveRooms() {
        return meetingRoomMapper.findAllActive();
    }

    public List<MeetingRoom> getAllRooms(String status, String keyword) {
        return meetingRoomMapper.findAll(status, keyword);
    }

    public MeetingRoom getById(Long id) {
        MeetingRoom room = meetingRoomMapper.findById(id);
        if (room == null || room.getDeletedAt() != null) {
            throw new BusinessException(404, "会议室不存在");
        }
        return room;
    }

    @Transactional
    public MeetingRoom create(MeetingRoom room) {
        if (room.getStatus() == null || room.getStatus().isBlank()) {
            room.setStatus("ENABLED");
        }
        meetingRoomMapper.insert(room);
        return room;
    }

    @Transactional
    public MeetingRoom update(MeetingRoom room) {
        MeetingRoom existing = getById(room.getId());
        existing.setName(room.getName());
        existing.setLocation(room.getLocation());
        existing.setCapacity(room.getCapacity());
        int updated = meetingRoomMapper.update(existing);
        if (updated == 0) {
            throw new BusinessException(409, "数据已被他人修改，请刷新后重试");
        }
        return existing;
    }

    @Transactional
    public MeetingRoom updateStatus(Long id, String status) {
        MeetingRoom room = getById(id);
        if (!"ENABLED".equalsIgnoreCase(status) && !"DISABLED".equalsIgnoreCase(status)) {
            throw new BusinessException(400, "状态值必须为 ENABLED 或 DISABLED");
        }
        meetingRoomMapper.updateStatus(id, status.toUpperCase());
        room.setStatus(status.toUpperCase());
        return room;
    }

    @Transactional
    public void delete(Long id) {
        MeetingRoom room = getById(id);
        meetingRoomMapper.softDelete(id);
    }
}
