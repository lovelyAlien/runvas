package com.runvas.auth.service;

public interface AppleTokenExchangeClient {

    String exchangeForRefreshToken(String authorizationCode);
}
