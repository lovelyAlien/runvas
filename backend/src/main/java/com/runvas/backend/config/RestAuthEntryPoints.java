package com.runvas.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runvas.backend.common.ApiErrorResponse;
import com.runvas.backend.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

// Spring Security가 컨트롤러까지 가기 전에 필터 단계에서 막는 요청(인증 없음/권한 없음)은
// GlobalExceptionHandler를 거치지 않고 스프링 기본 에러 바디({"timestamp":...,"status":403,...})를
// 내려준다. docs/api-contract.md 형식({"error":{"code","message"}})과 다르면 모바일이
// errorBody.error.code를 못 읽고 UNKNOWN_ERROR로 빠지는 문제가 있어, 이 두 핸들러로 같은
// 형식을 강제한다.
@Component
public class RestAuthEntryPoints implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
			throws java.io.IOException {
		writeError(response, ErrorCode.UNAUTHORIZED, "인증이 필요하거나 토큰이 유효하지 않습니다");
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
			throws java.io.IOException {
		writeError(response, ErrorCode.FORBIDDEN, "접근 권한이 없습니다");
	}

	private void writeError(HttpServletResponse response, ErrorCode code, String message) throws java.io.IOException {
		response.setStatus(code.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(
				objectMapper.writeValueAsString(ApiErrorResponse.of(code, message, List.of())));
	}
}
