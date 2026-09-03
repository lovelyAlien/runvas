package com.runvas.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.runvas.global.error.RunvasException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AppleHttpAuthClientTest {

    private static final String BUNDLE_ID = "com.runvas.mobile";
    private static final String JWKS_URI = "https://appleid.apple.com/auth/keys";

    @Test
    void verifyIdentityToken_유효한_토큰이면_providerUserId와_email을_반환한다() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String kid = "test-key-id";

        String jwksJson = buildJwksJson(kid, (RSAPublicKey) keyPair.getPublic());
        String identityToken = Jwts.builder()
                .header().add("kid", kid).and()
                .issuer("https://appleid.apple.com")
                .audience().add(BUNDLE_ID).and()
                .subject("apple-sub-789")
                .claim("email", "runner@example.com")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith((RSAPrivateKey) keyPair.getPrivate())
                .compact();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(req -> assertThat(req.getURI().toString()).isEqualTo(JWKS_URI))
                .andRespond(withSuccessJson(jwksJson));

        AppleHttpAuthClient client = new AppleHttpAuthClient(builder, JWKS_URI, BUNDLE_ID);

        AppleUserInfo info = client.verifyIdentityToken(identityToken);

        assertThat(info.providerUserId()).isEqualTo("apple-sub-789");
        assertThat(info.email()).isEqualTo("runner@example.com");
    }

    @Test
    void verifyIdentityToken_aud가_다르면_예외를_던진다() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String kid = "test-key-id-2";

        String jwksJson = buildJwksJson(kid, (RSAPublicKey) keyPair.getPublic());
        String identityToken = Jwts.builder()
                .header().add("kid", kid).and()
                .issuer("https://appleid.apple.com")
                .audience().add("com.other.app").and()
                .subject("apple-sub-999")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith((RSAPrivateKey) keyPair.getPrivate())
                .compact();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(req -> { }).andRespond(withSuccessJson(jwksJson));

        AppleHttpAuthClient client = new AppleHttpAuthClient(builder, JWKS_URI, BUNDLE_ID);

        assertThatThrownBy(() -> client.verifyIdentityToken(identityToken))
                .isInstanceOf(RunvasException.class);
    }

    private static String buildJwksJson(String kid, RSAPublicKey publicKey) {
        var jwk = Jwks.builder().key(publicKey).id(kid).build();
        return "{\"keys\":[" + Jwks.json(jwk) + "]}";
    }

    private static org.springframework.test.web.client.ResponseCreator withSuccessJson(String body) {
        return org.springframework.test.web.client.response.MockRestResponseCreators
                .withSuccess(body, MediaType.APPLICATION_JSON);
    }
}
