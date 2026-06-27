package com.runvas.backend.auth.dto;

// 카카오 SDK 연동 전, 모바일이 실제 JWT를 받아 Course API 등을 테스트할 수 있게 하는 개발용 요청.
// docs/api-contract.md의 공식 계약이 아니다 — DevAuthController.java 상단 주석 참고.
public record DevLoginRequest(String nickname) {
}
