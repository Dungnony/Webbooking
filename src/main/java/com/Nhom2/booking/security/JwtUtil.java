package com.Nhom2.booking.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import com.Nhom2.booking.enums.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    private final Key key;

    /**
     * Khoa ky token lay tu JWT_SECRET (.env hoac bien moi truong), khong viet cung trong code.
     * Thieu hoac ngan hon 32 ky tu -> dung khoa ngau nhien: van an toan,
     * nhung token cu het hieu luc moi lan server khoi dong lai.
     */
    @Autowired
    public JwtUtil(@Value("${jwt.secret:}") String secret) {
        if (secret == null || secret.length() < 32) {
            log.warn("JWT_SECRET chua dat (hoac ngan hon 32 ky tu): dung khoa ngau nhien, "
                    + "nguoi dung phai dang nhap lai moi lan server khoi dong lai");
            this.key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        } else {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Dung trong unit test: khoa ngau nhien. */
    public JwtUtil() {
        this(null);
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
