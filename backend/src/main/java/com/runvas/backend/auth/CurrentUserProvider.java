package com.runvas.backend.auth;

import com.runvas.global.security.RunvasPrincipal;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

	// Required 엔드포인트에서 호출 — SecurityConfig가 이미 인증을 강제했으므로 null이 아니다.
	public String requireUserId() {
		return extractUserId(SecurityContextHolder.getContext().getAuthentication());
	}

	// Optional 엔드포인트에서 호출 — 비로그인이면 null.
	public String currentUserIdOrNull() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null
				|| !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return null;
		}
		return extractUserId(authentication);
	}

	private String extractUserId(Authentication authentication) {
		Object principal = authentication.getPrincipal();
		if (principal instanceof RunvasPrincipal runvasPrincipal) {
			return runvasPrincipal.userId().toString();
		}
		return principal.toString();
	}
}
