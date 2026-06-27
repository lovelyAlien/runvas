package com.runvas.global.error;

public class RunvasException extends RuntimeException {

    private final ErrorCode errorCode;

    public RunvasException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public RunvasException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
