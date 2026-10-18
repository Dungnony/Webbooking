package com.Nhom2.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    // Injected instead of calling now() directly so time-based rules can be tested with a fixed clock.
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
