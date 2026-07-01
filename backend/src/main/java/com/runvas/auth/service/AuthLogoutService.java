package com.runvas.auth.service;

import org.springframework.stereotype.Service;

@Service
public class AuthLogoutService {

    private final TokenBlacklistService tokenBlacklistService;

    public AuthLogoutService(TokenBlacklistService tokenBlacklistService) {
        this.tokenBlacklistService = tokenBlacklistService;
    }

    public void logout(String token) {
        tokenBlacklistService.blacklist(token);
    }
}
