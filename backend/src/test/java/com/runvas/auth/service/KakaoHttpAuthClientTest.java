package com.runvas.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoHttpAuthClientTest {

    @Test
    void mapsKakaoUserPayload() {
        String json = """
                {
                  "id": 12345,
                  "kakao_account": {
                    "email": "runner@example.com",
                    "profile": {
                      "nickname": "Seoul Runner",
                      "profile_image_url": "https://example.com/profile.png"
                    }
                  }
                }
                """;

        KakaoUserInfo userInfo = KakaoHttpAuthClient.parseUserInfo(json);

        assertThat(userInfo.providerUserId()).isEqualTo("12345");
        assertThat(userInfo.email()).isEqualTo("runner@example.com");
        assertThat(userInfo.nickname()).isEqualTo("Seoul Runner");
        assertThat(userInfo.profileImageUrl()).isEqualTo("https://example.com/profile.png");
    }
}
