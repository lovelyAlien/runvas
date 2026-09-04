package com.runvas.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AppleClientSecretGeneratorTest {

    @Test
    void generate_필요한_클레임과_kid를_담아_ES256으로_서명한다()
            throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        String privateKeyPem = toPem(keyPair.getPrivate());

        AppleClientSecretGenerator generator = new AppleClientSecretGenerator(
                "TEAM123456",
                "KEY7890AB",
                "com.runvas.mobile",
                privateKeyPem
        );

        String clientSecret = generator.generate();

        Claims claims = Jwts.parser()
                .verifyWith((PublicKey) keyPair.getPublic())
                .build()
                .parseSignedClaims(clientSecret)
                .getPayload();

        assertThat(claims.getIssuer()).isEqualTo("TEAM123456");
        assertThat(claims.getSubject()).isEqualTo("com.runvas.mobile");
        assertThat(claims.getAudience()).containsExactly("https://appleid.apple.com");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void generate_프라이빗_키가_설정되지_않았으면_예외() {
        AppleClientSecretGenerator generator =
                new AppleClientSecretGenerator("TEAM123456", "KEY7890AB", "com.runvas.mobile", "");

        assertThatThrownBy(generator::generate).isInstanceOf(IllegalStateException.class);
    }

    private static String toPem(PrivateKey privateKey) {
        String base64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
    }
}
