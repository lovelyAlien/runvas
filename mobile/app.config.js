const fs = require('fs');
const path = require('path');

// EAS Build가 빌드 중에 주입하는 프로필 이름. 로컬 `npx expo start`(Expo Go/dev-client)
// 실행 시에는 비어있으므로 development로 취급한다 — 로컬 개발이 끊기면 안 되기 때문이다.
const buildProfile = process.env.EAS_BUILD_PROFILE ?? 'development';

// mobile-eas-build-production.yml이 릴리스 태그(mobile-v{semver})에서 뽑은 버전을 이 파일에
// 적어둔다. 환경변수가 아니라 파일인 이유: 이 프로젝트는 managed workflow라 네이티브 프로젝트가
// 저장소에 없고, `eas build`가 업로드한 프로젝트 트리 위에서 EAS 원격 빌드 워커가
// `expo prebuild` 단계에서 app.config.js를 다시 평가한다(EAS_BUILD_PROFILE도 그 시점에 원격에서
// 주입된다) — CI 러너에만 설정한 환경변수는 그 원격 평가에 전달된다는 보장이 없지만, 프로젝트
// 트리에 포함된 파일은 그대로 함께 올라간다.
// .gitignore에 올리지 않는다 — EAS Build는 업로드할 파일을 고를 때 기본적으로 .gitignore를
// 그대로 따르므로(.easignore가 없는 한), gitignore된 파일은 원격 빌드 워커에 아예 전달되지
// 않는다. 커밋도 하지 않는다 — CI 잡이 매번 새로 clone한 워크트리에 이 스텝에서만 파일을 만들고
// git add/commit을 하지 않으므로, 워크플로 실행이 끝나면 그 러너와 함께 사라진다.
function resolveVersion() {
  try {
    return fs.readFileSync(path.join(__dirname, '.release-version'), 'utf8').trim();
  } catch {
    return '1.0.0';
  }
}

// development: 로컬 사설 IP 백엔드(예: http://172.30.1.22:8080)로 평문 HTTP 요청이 필요해
// ATS를 전역으로 푼다. preview/production: 백엔드가 이미 HTTPS(sslip.io)라 예외가 필요 없고,
// 카카오맵 SDK가 내부적으로 불러오는 daumcdn.net의 HTTP 리소스만 예외로 좁힌다.
// (mobile/AGENTS.md "카카오 지도 SDK — iOS ATS / Android cleartext traffic 문제" 참고)
const appTransportSecurity =
  buildProfile === 'development'
    ? { NSAllowsArbitraryLoads: true }
    : {
        NSExceptionDomains: {
          'daumcdn.net': {
            NSIncludesSubdomains: true,
            NSExceptionAllowsInsecureHTTPLoads: true,
          },
        },
      };

module.exports = {
  expo: {
    name: 'RunSketch',
    slug: 'runvas-mobile',
    version: resolveVersion(),
    scheme: 'runvas',
    orientation: 'portrait',
    icon: './assets/icon.png',
    userInterfaceStyle: 'light',
    ios: {
      supportsTablet: false,
      bundleIdentifier: 'com.runvas.mobile',
      infoPlist: {
        NSLocationWhenInUseUsageDescription: '러닝 코스 생성을 위해 현재 위치가 필요합니다.',
        NSAppTransportSecurity: appTransportSecurity,
        ITSAppUsesNonExemptEncryption: false,
      },
    },
    android: {
      package: 'com.runvas.mobile',
      adaptiveIcon: {
        backgroundColor: '#E6F4FE',
        foregroundImage: './assets/android-icon-foreground.png',
        backgroundImage: './assets/android-icon-background.png',
        monochromeImage: './assets/android-icon-monochrome.png',
      },
      permissions: ['ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION'],
    },
    web: {
      favicon: './assets/favicon.png',
    },
    plugins: [
      [
        'expo-location',
        {
          locationWhenInUsePermission: '러닝 코스 생성을 위해 현재 위치가 필요합니다.',
        },
      ],
      'expo-secure-store',
      'expo-font',
      [
        'expo-build-properties',
        {
          android: {
            usesCleartextTraffic: true,
          },
        },
      ],
    ],
    extra: {
      eas: {
        projectId: 'ed81a4b8-1fbe-4a1e-b016-7387171b1299',
      },
    },
    owner: 'runvas',
  },
};
