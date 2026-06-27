package com.runvas.auth.service;

public interface KakaoAuthClient {

    KakaoUserInfo fetchUserInfo(String authorizationCode, String redirectUri);
}
