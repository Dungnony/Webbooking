package com.Nhom2.booking.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * API messages follow the request's Accept-Language (the frontend sends the selected UI language).
 * Vietnamese is the default; {@code messages.properties} holds it and {@code messages_en.properties} English.
 */
@Configuration
public class LocaleConfig {

    public static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("vi");
    private static final List<Locale> SUPPORTED_LOCALES = List.of(DEFAULT_LOCALE, Locale.ENGLISH);

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(DEFAULT_LOCALE);
        resolver.setSupportedLocales(SUPPORTED_LOCALES);
        return resolver;
    }

    @Bean
    public MessageSource messageSource() {
        return createMessageSource();
    }

    public static MessageSource createMessageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        // Otherwise a server running with an English JVM locale would answer Vietnamese requests in English.
        source.setFallbackToSystemLocale(false);
        return source;
    }
}
