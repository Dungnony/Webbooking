package com.Nhom2.booking.config;

import com.Nhom2.booking.security.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())

                .sessionManagement(sess ->
                        sess.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/uploads/**",
                                "/error"
                        ).permitAll()
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/api/webhook/**").permitAll()
                        // WebSocket handshake; the STOMP CONNECT frame is authenticated by StompAuthChannelInterceptor
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/hotels/**", "/rooms/**").permitAll()
                        .requestMatchers("/api/images/**").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/users/me", "/users/me/**").authenticated()
                        .requestMatchers("/users/**").hasRole("ADMIN")
                        .requestMatchers("/bookings/**").authenticated()
                        .requestMatchers("/notifications/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/hotels/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/hotels/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/hotels/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/rooms/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/rooms/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/rooms/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Cho phép các nguồn (Origins) nào được gọi tới Backend này
        configuration.setAllowedOriginPatterns(AllowedOrigins.PATTERNS);

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Cache-Control"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
