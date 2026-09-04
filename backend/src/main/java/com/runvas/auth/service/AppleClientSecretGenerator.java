package com.runvas.auth.service;

import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppleClientSecretGenerator {

    private static final Logger log = LoggerFactory.getLogger(AppleClientSecretGenerator.class);

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

        boolean configured = privateKey != null && teamId != null && !teamId.isBlank()
                && keyId != null && !keyId.isBlank();
        if (configured) {
            log.info("Apple client secret is configured (team {}, key {})", teamId, keyId);
        } else {
            log.warn("Apple client secret is not fully configured (APPLE_TEAM_ID / APPLE_KEY_ID / "
                    + "APPLE_PRIVATE_KEY) - Apple login will not exchange authorizationCode for a "
                    + "refresh token, and account deletions will not revoke Apple tokens.");
        }
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

    /**
     * Returns {@code null} - never throws - when the private key is absent or cannot be parsed.
     * This bean is constructed eagerly at Spring context startup, and in every environment except
     * real production (local dev, CI, every Spring context test in this project) the Apple
     * private-key config is unset or not yet correctly formatted. If this method's caller (the
     * constructor) let a parse failure propagate, the entire Spring application context would
     * fail to start - breaking every unrelated test and every non-production deployment, not just
     * Apple login. Failure is deferred to generate(), whose only callers (AppleAuthService,
     * AccountPurgeService) already treat it as best-effort and catch it.
     */
    private static PrivateKey parsePrivateKeyOrNull(String pem) {
        if (pem == null || pem.isBlank()) {
            return null;
        }
        try {
            String base64 = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("\\n", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(base64);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (Exception exception) {
            log.warn("APPLE_PRIVATE_KEY is set but could not be parsed as an EC private key; "
                    + "Apple client secret generation will fail until this is fixed", exception);
            return null;
        }
    }
}
