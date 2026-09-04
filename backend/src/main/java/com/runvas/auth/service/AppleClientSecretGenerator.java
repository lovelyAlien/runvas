package com.runvas.auth.service;

import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppleClientSecretGenerator {

    private final String teamId;
    private final String keyId;
    private final String bundleId;
    private final PrivateKey privateKey;

    public AppleClientSecretGenerator(
            @Value("${runvas.apple.team-id}") String teamId,
            @Value("${runvas.apple.key-id}") String keyId,
            @Value("${runvas.apple.bundle-id}") String bundleId,
            @Value("${runvas.apple.private-key}") String privateKeyPem
    ) {
        this.teamId = teamId;
        this.keyId = keyId;
        this.bundleId = bundleId;
        this.privateKey = parsePrivateKeyOrNull(privateKeyPem);
    }

    public String generate() {
        if (privateKey == null || teamId == null || teamId.isBlank() || keyId == null || keyId.isBlank()) {
            throw new IllegalStateException("Apple client secret is not configured");
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .header().add("kid", keyId).and()
                .issuer(teamId)
                .subject(bundleId)
                .audience().add("https://appleid.apple.com").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(5, ChronoUnit.MINUTES)))
                .signWith(privateKey, Jwts.SIG.ES256)
                .compact();
    }

    private static PrivateKey parsePrivateKeyOrNull(String pem) {
        if (pem == null || pem.isBlank()) {
            return null;
        }
        try {
            String base64 = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(base64);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid Apple private key", exception);
        }
    }
}
