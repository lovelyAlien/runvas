package com.runvas.backend.common;

import java.util.List;

public class ApiException extends RuntimeException {

	private final ErrorCode errorCode;
	private final List<FieldErrorDetail> details;

	public ApiException(ErrorCode errorCode, String message) {
		this(errorCode, message, List.of());
	}

	public ApiException(ErrorCode errorCode, String message, List<FieldErrorDetail> details) {
		super(message);
		this.errorCode = errorCode;
		this.details = details;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	public List<FieldErrorDetail> getDetails() {
		return details;
	}

	public record FieldErrorDetail(String field, String message) {
	}
}
