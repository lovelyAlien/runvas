package com.runvas.auth.service;

public record KakaoUserInfo(
        String providerUserId,
        String email,
        String nickname,
        String profileImageUrl
) {
}
