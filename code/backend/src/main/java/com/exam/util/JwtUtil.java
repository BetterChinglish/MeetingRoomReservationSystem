package com.exam.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtUtil(
            @Value("${app.jwt.secret:ThisIsASecretKeyForJwtSigningMeetingRoomReservationSystem2026!}") String secret,
            @Value("${app.jwt.expiration-seconds:28800}") long expirationSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(Long userId, String username, String name, String role, Long deptId) {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date exp = new Date(nowMillis + expirationSeconds * 1000);
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .header().add("typ", "JWT").and()
                .issuer("meeting-room-system")
                .subject(String.valueOf(userId))
                .id(jti)
                .claim("username", username)
                .claim("name", name)
                .claim("role", role)
                .claim("deptId", deptId)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isTokenExpired(Claims claims) {
        return claims == null || claims.getExpiration().before(new Date());
    }

    public long getRemainingSeconds(Claims claims) {
        if (claims == null) return 0;
        long exp = claims.getExpiration().getTime();
        long now = System.currentTimeMillis();
        return Math.max(0, (exp - now) / 1000);
    }
}
