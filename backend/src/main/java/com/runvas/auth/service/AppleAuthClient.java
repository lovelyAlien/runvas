package com.runvas.auth.service;

public interface AppleAuthClient {
    AppleUserInfo verifyIdentityToken(String identityToken);
}
