package com.exam;

import com.exam.entity.Department;
import com.exam.entity.MeetingRoom;
import com.exam.entity.User;
import com.exam.mapper.DepartmentMapper;
import com.exam.mapper.MeetingRoomMapper;
import com.exam.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class IntegrationDatabaseAndRedisTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private MeetingRoomMapper meetingRoomMapper;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testDatabaseInitializationAndSeededData() {
        User admin = userMapper.findByUsername("admin");
        assertNotNull(admin, "Admin user must be seeded");
        assertEquals("ADMIN", admin.getRole());

        User emp001 = userMapper.findByUsername("emp001");
        assertNotNull(emp001, "emp001 user must be seeded");
        assertEquals("EMPLOYEE", emp001.getRole());

        List<Department> departments = departmentMapper.findAllActive();
        assertTrue(departments.size() >= 5, "At least 5 departments should be seeded");

        List<MeetingRoom> rooms = meetingRoomMapper.findAllActive();
        assertTrue(rooms.size() >= 4, "At least 4 enabled rooms should be seeded");
    }

    @Test
    public void testRedisMockedPing() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get("ping:test")).thenReturn("pong");

        String val = stringRedisTemplate.opsForValue().get("ping:test");
        assertEquals("pong", val);
    }
}
