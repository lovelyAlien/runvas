package com.runvas.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AppleHttpRevokeClientTest {

    private MockRestServiceServer server;
    private AppleHttpRevokeClient client;

    @BeforeEach
    void setUp() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        AppleClientSecretGenerator clientSecretGenerator = new AppleClientSecretGenerator(
                "TEAM123456", "KEY7890AB", "com.runvas.mobile", testPrivateKeyPem());
        client = new AppleHttpRevokeClient(
                builder, clientSecretGenerator, "https://appleid.apple.com/auth/revoke", "com.runvas.mobile");
    }

    @Test
    void revoke_필요한_파라미터를_담아_보낸다() {
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    assertThat(body).contains("client_id=com.runvas.mobile");
                    assertThat(body).contains("token=apple-refresh-token-value");
                    assertThat(body).contains("token_type_hint=refresh_token");
                    assertThat(body).contains("client_secret=");
                })
                .andRespond(withSuccess());

        client.revoke("apple-refresh-token-value");

        server.verify();
    }

    @Test
    void revoke_Apple이_에러_응답을_주면_예외() {
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.revoke("apple-refresh-token-value"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String testPrivateKeyPem() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        String base64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
    }
}
