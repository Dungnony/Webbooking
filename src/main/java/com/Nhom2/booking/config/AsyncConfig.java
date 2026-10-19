package com.Nhom2.booking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// Lets slow side effects (rendering and mailing invoices) run off the request thread, e.g. the Sepay webhook.
@Configuration
@EnableAsync
public class AsyncConfig {
}
