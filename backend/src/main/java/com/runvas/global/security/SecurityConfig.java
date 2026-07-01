package com.runvas.global.security;

import com.runvas.auth.service.JwtProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider,
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
                        .requestMatchers(HttpMethod.POST, "/api/routes/pedestrian").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/courses/mine").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/courses").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/courses/{courseId}").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/courses/{courseId}").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
