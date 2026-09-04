package com.runvas.global.security;

import com.runvas.auth.service.JwtProvider;
import com.runvas.auth.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final TokenBlacklistService tokenBlacklistService = mock(TokenBlacklistService.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtProvider, tokenBlacklistService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesWhenTokenValidAndUserNotBanned() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtProvider.parseUserId("valid-token")).thenReturn(userId);
        when(tokenBlacklistService.isBlacklisted("valid-token")).thenReturn(false);
        when(tokenBlacklistService.isUserBanned(userId)).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void clearsContextWhenUserIsBannedEvenIfTokenNotBlacklisted() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtProvider.parseUserId("valid-token")).thenReturn(userId);
        when(tokenBlacklistService.isBlacklisted("valid-token")).thenReturn(false);
        when(tokenBlacklistService.isUserBanned(userId)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void clearsContextWhenTokenBlacklistedRegardlessOfBanStatus() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtProvider.parseUserId("blacklisted-token")).thenReturn(userId);
        when(tokenBlacklistService.isBlacklisted("blacklisted-token")).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer blacklisted-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
