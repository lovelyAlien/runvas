package com.runvas.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

	private final SecretKey key;
	private final long expirationSeconds;

	public JwtTokenProvider(
			@Value("${runvas.jwt.secret}") String secret,
			@Value("${runvas.jwt.expiration-seconds}") long expirationSeconds) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationSeconds = expirationSeconds;
	}

	public String createToken(String userId) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(userId)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(expirationSeconds, ChronoUnit.SECONDS)))
				.signWith(key)
				.compact();
	}

	// 토큰이 무효하면 null을 반환한다 — Optional 인증 엔드포인트는 이걸로 "비로그인"과 구분한다.
	public String parseUserId(String token) {
		try {
			Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
			return claims.getSubject();
		} catch (Exception ex) {
			return null;
		}
	}
}
