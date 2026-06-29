package com.runvas.backend.auth;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

	// Required 엔드포인트에서 호출 — SecurityConfig가 이미 인증을 강제했으므로 null이 아니다.
	public String requireUserId() {
		return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
	}

	// Optional 엔드포인트에서 호출 — 비로그인이면 null.
	// AnonymousAuthenticationFilter가 permitAll() 요청에도 isAuthenticated()=true인
	// AnonymousAuthenticationToken을 채워두므로, 그 타입은 명시적으로 비로그인으로 취급한다.
	public String currentUserIdOrNull() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null
				|| !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return null;
		}
		return (String) authentication.getPrincipal();
	}
}
