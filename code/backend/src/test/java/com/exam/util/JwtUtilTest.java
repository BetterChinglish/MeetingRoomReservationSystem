package com.exam.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil("ThisIsASecretKeyForJwtSigningMeetingRoomReservationSystem2026!", 3600);

    @Test
    public void testTokenGenerationAndParsing() {
        String token = jwtUtil.generateToken(2L, "emp001", "张伟", "EMPLOYEE", 1L);
        assertNotNull(token);

        Claims claims = jwtUtil.parseToken(token);
        assertNotNull(claims);
        assertEquals("2", claims.getSubject());
        assertEquals("emp001", claims.get("username", String.class));
        assertEquals("EMPLOYEE", claims.get("role", String.class));
        assertFalse(jwtUtil.isTokenExpired(claims));
        assertTrue(jwtUtil.getRemainingSeconds(claims) > 3500);
    }
}
