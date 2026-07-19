package com.runvas.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class KakaoHttpUnlinkClient implements KakaoUnlinkClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoHttpUnlinkClient.class);

    private final RestClient restClient;
    private final String unlinkUri;
    private final String adminKey;

    public KakaoHttpUnlinkClient(
            RestClient.Builder restClientBuilder,
            @Value("${runvas.kakao.unlink-uri}") String unlinkUri,
            @Value("${runvas.kakao.admin-key}") String adminKey
    ) {
        this.restClient = restClientBuilder.build();
        this.unlinkUri = unlinkUri;
        this.adminKey = adminKey;
    }

    @Override
    public void unlink(String providerUserId) {
        if (adminKey == null || adminKey.isBlank()) {
            log.warn("Kakao admin key is not configured; skipping unlink for provider user {}", providerUserId);
            return;
        }

        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", providerUserId);

        restClient.post()
                .uri(unlinkUri)
                .header("Authorization", "KakaoAK " + adminKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new IllegalStateException("Kakao unlink failed with status " + res.getStatusCode());
                })
                .toBodilessEntity();
    }
}
