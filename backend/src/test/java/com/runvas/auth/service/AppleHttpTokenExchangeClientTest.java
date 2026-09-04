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
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AppleHttpTokenExchangeClientTest {

    private MockRestServiceServer server;
    private AppleHttpTokenExchangeClient client;

    @BeforeEach
    void setUp() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        AppleClientSecretGenerator clientSecretGenerator = new AppleClientSecretGenerator(
                "TEAM123456", "KEY7890AB", "com.runvas.mobile", testPrivateKeyPem());
        client = new AppleHttpTokenExchangeClient(
                builder, clientSecretGenerator, "https://appleid.apple.com/auth/token", "com.runvas.mobile");
    }

    @Test
    void exchangeForRefreshToken_필요한_파라미터를_담아_보내고_refresh_token을_반환한다() {
        server.expect(requestTo("https://appleid.apple.com/auth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    assertThat(body).contains("client_id=com.runvas.mobile");
                    assertThat(body).contains("code=auth-code-1");
                    assertThat(body).contains("grant_type=authorization_code");
                    assertThat(body).contains("client_secret=");
                })
                .andRespond(withSuccess(
                        "{\"refresh_token\":\"refresh-token-value\"}", MediaType.APPLICATION_JSON));

        String refreshToken = client.exchangeForRefreshToken("auth-code-1");

        assertThat(refreshToken).isEqualTo("refresh-token-value");
        server.verify();
    }

    @Test
    void exchangeForRefreshToken_응답에_refresh_token이_없으면_예외() {
        server.expect(requestTo("https://appleid.apple.com/auth/token"))
                .andRespond(withSuccess("{\"access_token\":\"access-token-value\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchangeForRefreshToken("auth-code-2"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exchangeForRefreshToken_Apple이_에러_응답을_주면_예외() {
        server.expect(requestTo("https://appleid.apple.com/auth/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.exchangeForRefreshToken("auth-code-3"))
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
