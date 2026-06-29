package com.runvas.backend.auth.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

// 카카오 인가 코드를 액세스 토큰으로 교환하고, 그 토큰으로 사용자 정보를 조회한다.
// RUNVAS_KAKAO_CLIENT_ID/SECRET이 비어 있으면(로컬 개발) 호출이 실패하므로, 실제 앱 키가
// 준비되기 전까지는 이 클라이언트를 호출하지 않는 mock 로그인 경로를 따로 두는 것을 권장한다
// (mobile은 이미 AuthContext.mockLogin으로 그렇게 하고 있다).
@Component
public class KakaoOAuthClient {

	private final RestClient restClient = RestClient.create();
	private final String clientId;
	private final String clientSecret;

	public KakaoOAuthClient(
			@Value("${runvas.kakao.client-id}") String clientId,
			@Value("${runvas.kakao.client-secret}") String clientSecret) {
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	public KakaoUserInfo exchangeAndFetchUser(String authorizationCode, String redirectUri) {
		String kakaoAccessToken = exchangeToken(authorizationCode, redirectUri);
		return fetchUserInfo(kakaoAccessToken);
	}

	private String exchangeToken(String authorizationCode, String redirectUri) {
		try {
			var body = UriComponentsBuilder.newInstance()
					.queryParam("grant_type", "authorization_code")
					.queryParam("client_id", clientId)
					.queryParam("client_secret", clientSecret)
					.queryParam("redirect_uri", redirectUri)
					.queryParam("code", authorizationCode)
					.build()
					.getQuery();

			KakaoTokenResponse response = restClient.post()
					.uri("https://kauth.kakao.com/oauth/token")
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(body)
					.retrieve()
					.body(KakaoTokenResponse.class);

			if (response == null) {
				throw new ApiException(ErrorCode.UNAUTHORIZED, "카카오 인증 실패");
			}
			return response.accessToken();
		} catch (RestClientException ex) {
			throw new ApiException(ErrorCode.UNAUTHORIZED, "카카오 인증 실패");
		}
	}

	private KakaoUserInfo fetchUserInfo(String kakaoAccessToken) {
		try {
			KakaoUserResponse response = restClient.get()
					.uri("https://kapi.kakao.com/v2/user/me")
					.header("Authorization", "Bearer " + kakaoAccessToken)
					.retrieve()
					.body(KakaoUserResponse.class);

			if (response == null) {
				throw new ApiException(ErrorCode.UNAUTHORIZED, "카카오 사용자 정보 조회 실패");
			}
			String email = response.kakaoAccount() != null ? response.kakaoAccount().email() : null;
			return new KakaoUserInfo(String.valueOf(response.id()), email);
		} catch (RestClientException ex) {
			throw new ApiException(ErrorCode.UNAUTHORIZED, "카카오 사용자 정보 조회 실패");
		}
	}

	private record KakaoTokenResponse(@JsonProperty("access_token") String accessToken) {
	}

	private record KakaoUserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {
	}

	private record KakaoAccount(String email) {
	}

	public record KakaoUserInfo(String providerUserId, String email) {
	}
}
