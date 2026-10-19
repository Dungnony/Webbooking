package com.Nhom2.booking.exception;

/**
 * A rule violation the user should see, identified by a message code from {@code messages*.properties}
 * so the API can answer in the caller's language. Mapped to HTTP 400 unless a subclass says otherwise.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final transient Object[] args;

    public BusinessException(String code, Object... args) {
        super(code);
        this.code = code;
        this.args = args.clone();
    }

    protected BusinessException(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
        this.args = new Object[0];
    }

    public String getCode() {
        return code;
    }

    public Object[] getArgs() {
        return args.clone();
    }
}
