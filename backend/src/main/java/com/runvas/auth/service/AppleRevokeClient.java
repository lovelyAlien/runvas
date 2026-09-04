package com.runvas.auth.service;

public interface AppleRevokeClient {

    void revoke(String refreshToken);
}
