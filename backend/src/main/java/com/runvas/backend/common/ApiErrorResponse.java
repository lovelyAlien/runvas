package com.runvas.backend.common;

import java.util.List;

// docs/api-contract.md 공통 에러 응답 형식: { "error": { "code", "message", "details"? } }
public record ApiErrorResponse(ErrorBody error) {

	public record ErrorBody(String code, String message, List<ApiException.FieldErrorDetail> details) {
	}

	public static ApiErrorResponse of(ErrorCode code, String message, List<ApiException.FieldErrorDetail> details) {
		return new ApiErrorResponse(new ErrorBody(code.name(), message, details.isEmpty() ? null : details));
	}
}
