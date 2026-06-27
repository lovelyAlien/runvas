package com.runvas.backend.config;

import com.runvas.backend.auth.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// docs/api-contract.md 인증 정책(None/Optional/Required)을 엔드포인트별로 매핑한다.
// Required로 표시된 패턴만 authenticated(), 나머지는 permitAll() — JwtAuthenticationFilter가
// 유효한 토큰일 때만 SecurityContext를 채우므로 Optional 엔드포인트는 컨트롤러에서
// principal 유무로 로그인/비로그인을 직접 분기한다.
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final RestAuthEntryPoints restAuthEntryPoints;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
				.httpBasic(httpBasic -> httpBasic.disable())
				.formLogin(formLogin -> formLogin.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// 필터 단계에서 막히는 요청(토큰 없음/무효, 권한 없음)도 docs/api-contract.md
				// 형식({"error":{"code","message"}})으로 응답하게 강제 — 안 하면 Spring 기본
				// 에러 바디({"timestamp":...})가 나가서 모바일이 UNKNOWN_ERROR로 잘못 처리한다.
				.exceptionHandling(exceptionHandling -> exceptionHandling
						.authenticationEntryPoint(restAuthEntryPoints)
						.accessDeniedHandler(restAuthEntryPoints))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.GET, "/me", "/me/bookmarked-courses").authenticated()
						.requestMatchers(HttpMethod.PATCH, "/me").authenticated()
						.requestMatchers(HttpMethod.POST, "/routes/pedestrian").authenticated()
						.requestMatchers(HttpMethod.POST, "/courses").authenticated()
						.requestMatchers(HttpMethod.PATCH, "/courses/*").authenticated()
						.requestMatchers(HttpMethod.DELETE, "/courses/*").authenticated()
						.requestMatchers(HttpMethod.POST, "/courses/*/bookmarks").authenticated()
						.requestMatchers(HttpMethod.DELETE, "/courses/*/bookmarks").authenticated()
						.requestMatchers(HttpMethod.POST, "/posts").authenticated()
						.requestMatchers(HttpMethod.PATCH, "/posts/*").authenticated()
						.requestMatchers(HttpMethod.DELETE, "/posts/*").authenticated()
						.requestMatchers(HttpMethod.POST, "/posts/*/comments").authenticated()
						.requestMatchers(HttpMethod.PATCH, "/comments/*").authenticated()
						.requestMatchers(HttpMethod.DELETE, "/comments/*").authenticated()
						.requestMatchers(HttpMethod.PUT, "/likes/**").authenticated()
						.requestMatchers(HttpMethod.DELETE, "/likes/**").authenticated()
						.anyRequest().permitAll())
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
