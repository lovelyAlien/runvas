package com.runvas.auth.service;

import com.runvas.global.error.ErrorCode;
import com.runvas.global.error.RunvasException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import java.security.Key;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AppleHttpAuthClient implements AppleAuthClient {

    private static final String ISSUER = "https://appleid.apple.com";

    private final RestClient restClient;
    private final String jwksUri;
    private final String bundleId;

    public AppleHttpAuthClient(
            RestClient.Builder restClientBuilder,
            @Value("${runvas.apple.jwks-uri}") String jwksUri,
            @Value("${runvas.apple.bundle-id}") String bundleId
    ) {
        this.restClient = restClientBuilder.build();
        this.jwksUri = jwksUri;
        this.bundleId = bundleId;
    }

    @Override
    public AppleUserInfo verifyIdentityToken(String identityToken) {
        try {
            Locator<Key> keyLocator = header -> resolveKey(header);
            Claims claims = Jwts.parser()
                    .keyLocator(keyLocator)
                    .build()
                    .parseSignedClaims(identityToken)
                    .getPayload();

            if (!ISSUER.equals(claims.getIssuer())) {
                throw new RunvasException(ErrorCode.UNAUTHORIZED, "Apple authentication failed");
            }
            if (!claims.getAudience().contains(bundleId)) {
                throw new RunvasException(ErrorCode.UNAUTHORIZED, "Apple authentication failed");
            }

            String email = claims.get("email", String.class);
            return new AppleUserInfo(claims.getSubject(), email);
        } catch (RunvasException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RunvasException(ErrorCode.UNAUTHORIZED, "Apple authentication failed");
        }
    }

    private Key resolveKey(Header header) {
        String kid = (String) header.get("kid");
        String jwksJson = restClient.get()
                .uri(jwksUri)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new RunvasException(ErrorCode.UNAUTHORIZED, "Apple authentication failed");
                })
                .body(String.class);

        JwkSet jwkSet = Jwks.setParser().build().parse(jwksJson);
        return jwkSet.getKeys().stream()
                .filter(jwk -> kid != null && kid.equals(jwk.getId()))
                .findFirst()
                .map(Jwk::toKey)
                .orElseThrow(() -> new RunvasException(ErrorCode.UNAUTHORIZED, "Apple authentication failed"));
    }
}
