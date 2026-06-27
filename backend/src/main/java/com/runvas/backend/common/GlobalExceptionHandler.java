package com.runvas.backend.common;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
		return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
				.body(ApiErrorResponse.of(ex.getErrorCode(), ex.getMessage(), ex.getDetails()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
		List<ApiException.FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
				.map(fieldError -> new ApiException.FieldErrorDetail(
						fieldError.getField(), fieldError.getDefaultMessage()))
				.toList();
		return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
				.body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, "Invalid request body", details));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
		return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
				.body(ApiErrorResponse.of(ErrorCode.INTERNAL_ERROR, "Unexpected server error", List.of()));
	}
}
