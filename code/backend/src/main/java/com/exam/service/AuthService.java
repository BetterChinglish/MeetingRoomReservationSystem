package com.exam.service;

import com.exam.common.context.UserContext;
import com.exam.common.exception.BusinessException;
import com.exam.common.result.ResultCode;
import com.exam.dto.LoginRequest;
import com.exam.dto.LoginResponse;
import com.exam.entity.Department;
import com.exam.entity.User;
import com.exam.mapper.DepartmentMapper;
import com.exam.mapper.UserMapper;
import com.exam.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.auth.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.auth.lockout-minutes:15}")
    private int lockoutMinutes;

    @Value("${app.auth.ip-max-failed-attempts:10}")
    private int ipMaxFailedAttempts;

    @Value("${app.auth.ip-rate-limit-minutes:10}")
    private int ipRateLimitMinutes;

    @Value("${app.jwt.expiration-seconds:28800}")
    private long jwtExpirationSeconds;

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String username = request.getUsername().trim().toLowerCase();
        String clientIp = getClientIp(httpRequest);

        // 1. Check IP rate limit
        String ipKey = "auth:ip_limit:" + clientIp;
        try {
            String ipCountStr = stringRedisTemplate.opsForValue().get(ipKey);
            if (ipCountStr != null && Integer.parseInt(ipCountStr) >= ipMaxFailedAttempts) {
                log.warn("IP {} exceeded max failed login attempts", clientIp);
                throw new BusinessException(ResultCode.TOO_MANY_REQUESTS.getCode(), "登录尝试过于频繁，请稍后再试");
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.warn("Redis unavailable for IP rate limiting check: {}", e.getMessage());
        }

        // 2. Check Account lockout
        String lockoutKey = "auth:lockout:" + username;
        try {
            String failCountStr = stringRedisTemplate.opsForValue().get(lockoutKey);
            if (failCountStr != null && Integer.parseInt(failCountStr) >= maxFailedAttempts) {
                log.warn("Account {} is locked out due to multiple failed logins", username);
                throw new BusinessException(ResultCode.ACCOUNT_LOCKED.getCode(), "账号已被临时锁定，请15分钟后再试");
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.warn("Redis unavailable for account lockout check: {}", e.getMessage());
        }

        // 3. Query user
        User user = userMapper.findByUsername(username);
        boolean matched = user != null && passwordEncoder.matches(request.getPassword(), user.getPassword());

        if (!matched) {
            // Record failure
            recordLoginFailure(lockoutKey, ipKey);
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }

        // 4. Success -> Clear account lockout counter
        try {
            stringRedisTemplate.delete(lockoutKey);
        } catch (Exception e) {
            log.warn("Redis unavailable to clear lockout key: {}", e.getMessage());
        }

        // 5. Query Dept Name
        Department dept = departmentMapper.findById(user.getDeptId());
        String deptName = (dept != null) ? dept.getName() : "未知部门";

        // 6. Generate JWT
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getName(), user.getRole(), user.getDeptId());

        return LoginResponse.builder()
                .token(token)
                .expiresIn(jwtExpirationSeconds)
                .user(LoginResponse.UserInfo.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .name(user.getName())
                        .role(user.getRole())
                        .deptId(user.getDeptId())
                        .deptName(deptName)
                        .build())
                .build();
    }

    public void logout(String authHeader) {
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return;
        }
        String token = authHeader.substring(7).trim();
        var claims = jwtUtil.parseToken(token);
        if (claims != null && StringUtils.hasText(claims.getId())) {
            long remaining = jwtUtil.getRemainingSeconds(claims);
            if (remaining > 0) {
                try {
                    String blacklistKey = "auth:blacklist:" + claims.getId();
                    stringRedisTemplate.opsForValue().set(blacklistKey, "revoked", remaining, TimeUnit.SECONDS);
                    log.info("Token jti {} added to blacklist for {}s", claims.getId(), remaining);
                } catch (Exception e) {
                    log.warn("Redis unavailable for logout blacklist: {}", e.getMessage());
                }
            }
        }
    }

    public LoginResponse.UserInfo getCurrentUserInfo() {
        UserContext.CurrentUser current = UserContext.get();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Department dept = departmentMapper.findById(current.getDeptId());
        String deptName = (dept != null) ? dept.getName() : "未知部门";

        return LoginResponse.UserInfo.builder()
                .userId(current.getUserId())
                .username(current.getUsername())
                .name(current.getName())
                .role(current.getRole())
                .deptId(current.getDeptId())
                .deptName(deptName)
                .build();
    }

    private void recordLoginFailure(String lockoutKey, String ipKey) {
        try {
            Long fails = stringRedisTemplate.opsForValue().increment(lockoutKey);
            if (fails != null && fails == 1) {
                stringRedisTemplate.expire(lockoutKey, lockoutMinutes, TimeUnit.MINUTES);
            }

            Long ipFails = stringRedisTemplate.opsForValue().increment(ipKey);
            if (ipFails != null && ipFails == 1) {
                stringRedisTemplate.expire(ipKey, ipRateLimitMinutes, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for recordLoginFailure: {}", e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xf) && !"unknown".equalsIgnoreCase(xf)) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
