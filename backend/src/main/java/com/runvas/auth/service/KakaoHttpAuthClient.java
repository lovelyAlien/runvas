package com.runvas.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runvas.global.error.ErrorCode;
import com.runvas.global.error.RunvasException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class KakaoHttpAuthClient implements KakaoAuthClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String tokenUri;
    private final String userInfoUri;
    private final String restApiKey;
    private final String clientSecret;

    public KakaoHttpAuthClient(
            RestClient.Builder restClientBuilder,
            @Value("${runvas.kakao.token-uri}") String tokenUri,
            @Value("${runvas.kakao.user-info-uri}") String userInfoUri,
            @Value("${runvas.kakao.rest-api-key}") String restApiKey,
            @Value("${runvas.kakao.client-secret}") String clientSecret
    ) {
        this.restClient = restClientBuilder.build();
        this.tokenUri = tokenUri;
        this.userInfoUri = userInfoUri;
        this.restApiKey = restApiKey;
        this.clientSecret = clientSecret;
    }

    @Override
    public KakaoUserInfo fetchUserInfo(String authorizationCode, String redirectUri) {
        String kakaoAccessToken = exchangeAccessToken(authorizationCode, redirectUri);
        String userInfoJson = restClient.get()
                .uri(userInfoUri)
                .header("Authorization", "Bearer " + kakaoAccessToken)
                .retrieve()
                .body(String.class);
        return parseUserInfo(userInfoJson);
    }

    private String exchangeAccessToken(String authorizationCode, String redirectUri) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", restApiKey);
        form.add("redirect_uri", redirectUri);
        form.add("code", authorizationCode);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        String tokenJson = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        return parseAccessToken(tokenJson);
    }

    static String parseAccessToken(String json) {
        try {
            String token = OBJECT_MAPPER.readTree(json).path("access_token").asText();
            if (token == null || token.isBlank()) {
                throw new RunvasException(ErrorCode.UNAUTHORIZED, "Kakao authentication failed");
            }
            return token;
        } catch (RunvasException e) {
            throw e;
        } catch (Exception exception) {
            throw new RunvasException(ErrorCode.UNAUTHORIZED, "Kakao authentication failed");
        }
    }

    static KakaoUserInfo parseUserInfo(String json) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode account = root.path("kakao_account");
            JsonNode profile = account.path("profile");
            return new KakaoUserInfo(
                    root.path("id").asText(),
                    textOrNull(account.path("email")),
                    textOrNull(profile.path("nickname")),
                    textOrNull(profile.path("profile_image_url"))
            );
        } catch (Exception exception) {
            throw new RunvasException(ErrorCode.UNAUTHORIZED, "Kakao authentication failed");
        }
    }

    private static String textOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }
}
