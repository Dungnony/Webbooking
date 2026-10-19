package com.Nhom2.booking.exception;

public class NotFoundException extends BusinessException {

    public NotFoundException(String code, Object... args) {
        super(code, args);
    }
}
