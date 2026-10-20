package com.Nhom2.booking.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import com.Nhom2.booking.enums.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    private final Key key;

    // Khoa lay tu JWT_SECRET (.env hoac bien moi truong), khong viet cung trong code
    public JwtUtil(@Value("${jwt.secret:}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "Thieu JWT_SECRET (it nhat 32 ky tu) trong file .env hoac bien moi truong");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username, UserRole role) {

        return Jwts.builder()
                .setSubject(username)
                .claim("role", role.name())
                .setIssuedAt(new Date())
                .setExpiration(
                        new Date(System.currentTimeMillis() + 86400000)
                )
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {

        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public String extractRole(String token) {

        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("role", String.class);
    }
}
