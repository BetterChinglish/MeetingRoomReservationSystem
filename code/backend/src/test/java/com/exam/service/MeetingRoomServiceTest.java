package com.exam.service;

import com.exam.common.exception.BusinessException;
import com.exam.entity.MeetingRoom;
import com.exam.mapper.MeetingRoomMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MeetingRoomServiceTest {

    @Mock
    private MeetingRoomMapper meetingRoomMapper;

    @InjectMocks
    private MeetingRoomService meetingRoomService;

    @Test
    public void testGetActiveRooms() {
        MeetingRoom r1 = MeetingRoom.builder().id(1L).name("第一会议室").status("ENABLED").build();
        when(meetingRoomMapper.findAllActive()).thenReturn(List.of(r1));

        List<MeetingRoom> rooms = meetingRoomService.getActiveRooms();
        assertEquals(1, rooms.size());
        assertEquals("ENABLED", rooms.get(0).getStatus());
    }

    @Test
    public void testUpdateStatusSuccess() {
        MeetingRoom r1 = MeetingRoom.builder().id(1L).name("第一会议室").status("ENABLED").build();
        when(meetingRoomMapper.findById(1L)).thenReturn(r1);
        when(meetingRoomMapper.updateStatus(1L, "DISABLED")).thenReturn(1);

        MeetingRoom updated = meetingRoomService.updateStatus(1L, "DISABLED");
        assertEquals("DISABLED", updated.getStatus());
        verify(meetingRoomMapper).updateStatus(1L, "DISABLED");
    }

    @Test
    public void testUpdateStatusInvalid() {
        MeetingRoom r1 = MeetingRoom.builder().id(1L).name("第一会议室").status("ENABLED").build();
        when(meetingRoomMapper.findById(1L)).thenReturn(r1);

        BusinessException ex = assertThrows(BusinessException.class, () -> meetingRoomService.updateStatus(1L, "INVALID"));
        assertEquals(400, ex.getCode());
    }
}
