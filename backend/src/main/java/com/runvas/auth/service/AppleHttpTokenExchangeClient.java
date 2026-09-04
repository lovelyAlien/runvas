package com.runvas.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class AppleHttpTokenExchangeClient implements AppleTokenExchangeClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final AppleClientSecretGenerator clientSecretGenerator;
    private final String tokenUri;
    private final String bundleId;

    public AppleHttpTokenExchangeClient(
            RestClient.Builder restClientBuilder,
            AppleClientSecretGenerator clientSecretGenerator,
            @Value("${runvas.apple.token-uri}") String tokenUri,
            @Value("${runvas.apple.bundle-id}") String bundleId
    ) {
        this.restClient = restClientBuilder.build();
        this.clientSecretGenerator = clientSecretGenerator;
        this.tokenUri = tokenUri;
        this.bundleId = bundleId;
    }

    @Override
    public String exchangeForRefreshToken(String authorizationCode) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", bundleId);
        form.add("client_secret", clientSecretGenerator.generate());
        form.add("code", authorizationCode);
        form.add("grant_type", "authorization_code");

        String json = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new IllegalStateException("Apple token exchange failed with status " + res.getStatusCode());
                })
                .body(String.class);

        return parseRefreshToken(json);
    }

    static String parseRefreshToken(String json) {
        try {
            String token = OBJECT_MAPPER.readTree(json).path("refresh_token").asText();
            if (token.isBlank()) {
                throw new IllegalStateException("Apple token response missing refresh_token");
            }
            return token;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse Apple token response", exception);
        }
    }
}
