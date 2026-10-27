package com.exam.service;

import com.exam.common.context.UserContext;
import com.exam.common.exception.BusinessException;
import com.exam.dto.LoginRequest;
import com.exam.dto.LoginResponse;
import com.exam.entity.Department;
import com.exam.entity.User;
import com.exam.mapper.DepartmentMapper;
import com.exam.mapper.UserMapper;
import com.exam.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private DepartmentMapper departmentMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    public void setup() {
        ReflectionTestUtils.setField(authService, "maxFailedAttempts", 5);
        ReflectionTestUtils.setField(authService, "lockoutMinutes", 15);
        ReflectionTestUtils.setField(authService, "ipMaxFailedAttempts", 10);
        ReflectionTestUtils.setField(authService, "ipRateLimitMinutes", 10);
        ReflectionTestUtils.setField(authService, "jwtExpirationSeconds", 28800L);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    public void testLoginSuccess() {
        LoginRequest req = new LoginRequest();
        req.setUsername("EMP001");
        req.setPassword("Password123!");

        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(valueOperations.get("auth:ip_limit:127.0.0.1")).thenReturn(null);
        when(valueOperations.get("auth:lockout:emp001")).thenReturn(null);

        User mockUser = User.builder()
                .id(2L)
                .username("emp001")
                .password("$2a$10$hashed")
                .name("张伟")
                .role("EMPLOYEE")
                .deptId(1L)
                .build();
        when(userMapper.findByUsername("emp001")).thenReturn(mockUser);
        when(passwordEncoder.matches("Password123!", "$2a$10$hashed")).thenReturn(true);

        Department mockDept = Department.builder().id(1L).name("研发部").build();
        when(departmentMapper.findById(1L)).thenReturn(mockDept);
        when(jwtUtil.generateToken(2L, "emp001", "张伟", "EMPLOYEE", 1L)).thenReturn("mock.jwt.token");

        LoginResponse resp = authService.login(req, httpRequest);

        assertNotNull(resp);
        assertEquals("mock.jwt.token", resp.getToken());
        assertEquals("emp001", resp.getUser().getUsername());
        assertEquals("研发部", resp.getUser().getDeptName());
        verify(stringRedisTemplate).delete("auth:lockout:emp001");
    }

    @Test
    public void testLoginAccountLockout() {
        LoginRequest req = new LoginRequest();
        req.setUsername("emp001");
        req.setPassword("123456");

        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(valueOperations.get("auth:ip_limit:127.0.0.1")).thenReturn(null);
        when(valueOperations.get("auth:lockout:emp001")).thenReturn("5");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req, httpRequest));
        assertEquals(423, ex.getCode());
        assertTrue(ex.getMessage().contains("锁定"));
    }

    @Test
    public void testLoginInvalidCredentials() {
        LoginRequest req = new LoginRequest();
        req.setUsername("emp001");
        req.setPassword("wrongpassword");

        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(valueOperations.get("auth:ip_limit:127.0.0.1")).thenReturn(null);
        when(valueOperations.get("auth:lockout:emp001")).thenReturn(null);

        User mockUser = User.builder()
                .id(2L)
                .username("emp001")
                .password("$2a$10$hashed")
                .build();
        when(userMapper.findByUsername("emp001")).thenReturn(mockUser);
        when(passwordEncoder.matches("wrongpassword", "$2a$10$hashed")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req, httpRequest));
        assertEquals(401, ex.getCode());
        assertEquals("用户名或密码错误", ex.getMessage());
        verify(valueOperations).increment("auth:lockout:emp001");
    }
}
