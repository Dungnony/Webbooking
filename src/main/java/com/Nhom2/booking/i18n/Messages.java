package com.Nhom2.booking.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Resolves message codes in the language of the current request. */
@Component
public class Messages {

    private final MessageSource messageSource;

    public Messages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** Falls back to the code itself so a missing translation degrades instead of failing the request. */
    public String get(String code, Object... args) {
        return forLocale(LocaleContextHolder.getLocale(), code, args);
    }

    /** For text produced outside a request (background jobs) or stored for later, where the language is explicit. */
    public String forLocale(Locale locale, String code, Object... args) {
        return messageSource.getMessage(code, args, code, locale);
    }
}
