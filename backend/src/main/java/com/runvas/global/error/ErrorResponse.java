package com.runvas.global.error;

import java.util.List;

public record ErrorResponse(ErrorBody error) {

    public static ErrorResponse of(ErrorCode code, String message, List<FieldErrorDetail> details) {
        return new ErrorResponse(new ErrorBody(code.name(), message, details));
    }

    public record ErrorBody(String code, String message, List<FieldErrorDetail> details) {
    }
}
