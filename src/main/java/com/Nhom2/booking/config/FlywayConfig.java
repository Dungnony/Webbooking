package com.Nhom2.booking.config;

import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    /**
     * The existing database was created by Hibernate (ddl-auto=update) before Flyway was added. On first run
     * Flyway records it as version 1 instead of re-running V1, then applies V2 onwards. A brand-new empty
     * database runs V1 normally.
     */
    @Bean
    public FlywayConfigurationCustomizer baselineExistingSchema() {
        return configuration -> configuration
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .baselineDescription("Schema created by Hibernate before Flyway");
    }
}
