package com.exam.interceptor;

import com.exam.annotation.RequireAdmin;
import com.exam.common.context.UserContext;
import com.exam.common.result.ApiResponse;
import com.exam.common.result.ResultCode;
import com.exam.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "请先登录（缺少凭据）");
            return false;
        }

        String token = authHeader.substring(7).trim();
        Claims claims = jwtUtil.parseToken(token);
        if (claims == null || jwtUtil.isTokenExpired(claims)) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "登录凭据已过期或无效，请重新登录");
            return false;
        }

        String jti = claims.getId();
        if (StringUtils.hasText(jti)) {
            String blacklistKey = "auth:blacklist:" + jti;
            Boolean isBlacklisted = stringRedisTemplate.hasKey(blacklistKey);
            if (Boolean.TRUE.equals(isBlacklisted)) {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "该登录会话已登出注销，请重新登录");
                return false;
            }
        }

        Long userId = Long.valueOf(claims.getSubject());
        String username = claims.get("username", String.class);
        String name = claims.get("name", String.class);
        String role = claims.get("role", String.class);
        Long deptId = claims.get("deptId", Long.class);

        UserContext.CurrentUser currentUser = UserContext.CurrentUser.builder()
                .userId(userId)
                .username(username)
                .name(name)
                .role(role)
                .deptId(deptId)
                .jti(jti)
                .build();
        UserContext.set(currentUser);

        // Check @RequireAdmin
        if (handler instanceof HandlerMethod handlerMethod) {
            boolean hasAdminAnnotation = handlerMethod.hasMethodAnnotation(RequireAdmin.class)
                    || handlerMethod.getBeanType().isAnnotationPresent(RequireAdmin.class);
            if (hasAdminAnnotation && !currentUser.isAdmin()) {
                sendError(response, HttpServletResponse.SC_FORBIDDEN, "无权限执行此管理操作");
                return false;
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Object> error = ApiResponse.error(status, message);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
