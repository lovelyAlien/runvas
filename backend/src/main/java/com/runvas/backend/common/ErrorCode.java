package com.runvas.backend.common;

import org.springframework.http.HttpStatus;

// docs/api-contract.md 공통 에러 응답 형식의 code 값과 1:1 대응.
public enum ErrorCode {
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
	FORBIDDEN(HttpStatus.FORBIDDEN),
	NOT_FOUND(HttpStatus.NOT_FOUND),
	CONFLICT(HttpStatus.CONFLICT),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus httpStatus;

	ErrorCode(HttpStatus httpStatus) {
		this.httpStatus = httpStatus;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}
}
