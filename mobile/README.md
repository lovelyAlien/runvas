# Runvas Mobile

Runvas 모바일 앱 구현을 관리하는 디렉토리입니다.

화면, 지도 인터랙션, API 연동은 `../docs/`의 공통 기준에 맞춰 구현합니다.

작업 시작 전 [CLAUDE.md](./CLAUDE.md), [AGENTS.md](./AGENTS.md), [WORKLOG.md](./WORKLOG.md)를 먼저 확인하세요.

## 시작하기

```bash
npm install
cp .env.example .env   # Kakao/T-MAP 키, API_BASE_URL 입력
npm start
```

## 기술 스택

- Expo (React Native, TypeScript)
- 지도: Kakao Maps JavaScript SDK (WebView)
- 보행자 경로 탐색: T-MAP
- 위치: expo-location (포그라운드)
- 경로 내보내기: GPX (expo-file-system, expo-sharing)
