package com.runvas.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class AppleHttpRevokeClient implements AppleRevokeClient {

    private final RestClient restClient;
    private final AppleClientSecretGenerator clientSecretGenerator;
    private final String revokeUri;
    private final String bundleId;

    public AppleHttpRevokeClient(
            RestClient.Builder restClientBuilder,
            AppleClientSecretGenerator clientSecretGenerator,
            @Value("${runvas.apple.revoke-uri}") String revokeUri,
            @Value("${runvas.apple.bundle-id}") String bundleId
    ) {
        this.restClient = restClientBuilder.build();
        this.clientSecretGenerator = clientSecretGenerator;
        this.revokeUri = revokeUri;
        this.bundleId = bundleId;
    }

    @Override
    public void revoke(String refreshToken) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", bundleId);
        form.add("client_secret", clientSecretGenerator.generate());
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");

        restClient.post()
                .uri(revokeUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    String body = new String(res.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    throw new IllegalStateException(
                            "Apple revoke failed with status " + res.getStatusCode() + ": " + body);
                })
                .toBodilessEntity();
    }
}
