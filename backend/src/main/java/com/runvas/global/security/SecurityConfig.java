package com.runvas.global.security;

import com.runvas.auth.service.JwtProvider;
import com.runvas.auth.service.TokenBlacklistService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider,
                                            TokenBlacklistService tokenBlacklistService,
                                            RunvasAuthenticationEntryPoint authenticationEntryPoint,
                                            RunvasAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/kakao", "/api/auth/dev-login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/courses", "/api/courses/{courseId}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/{postId}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/posts/{postId}/comments").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/routes/pedestrian").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/courses/mine").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/courses").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/courses/{courseId}").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/courses/{courseId}").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/likes/{targetType}/{targetId}").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/likes/{targetType}/{targetId}").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/courses/{courseId}/bookmarks").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/courses/{courseId}/bookmarks").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/me/bookmarked-courses").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/courses/{courseId}/comments").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/courses/{courseId}/comments/{commentId}/replies").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/courses/{courseId}/comments").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/courses/{courseId}/comments/{commentId}").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/courses/{courseId}/comments/{commentId}").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider, tokenBlacklistService),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
