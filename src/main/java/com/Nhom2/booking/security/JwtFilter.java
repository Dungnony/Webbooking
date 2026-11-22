package com.Nhom2.booking.security;

import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.UserRole;
import com.Nhom2.booking.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Autowired
    public JwtFilter(JwtUtil jwtUtil, ObjectProvider<UserRepository> userRepositoryProvider) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepositoryProvider != null ? userRepositoryProvider.getIfAvailable() : null;
    }

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
        this.userRepository = null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                Claims claims = jwtUtil.parseClaims(token);
                String username = claims.getSubject();

                if (username != null && !username.isBlank()) {
                    String effectiveRole = claims.get("role", String.class);
                    if (userRepository != null) {
                        User liveUser = userRepository.findByUsername(username).orElse(null);
                        if (liveUser == null) {
                            filterChain.doFilter(request, response);
                            return;
                        }
                        effectiveRole = liveUser.getRole() != null ? liveUser.getRole().name() : UserRole.USER.name();
                    }

                    if (effectiveRole != null && !effectiveRole.isBlank()) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        username,
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + effectiveRole))
                                );
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (JwtException | IllegalArgumentException ex) {
                // Invalid token -> skip authentication and let Spring Security enforce access rules.
            }
        }

        filterChain.doFilter(request, response);
    }
}
