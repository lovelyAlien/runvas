import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import { RoutePoint } from '../types';
import { buildGpxString } from './gpx';

// 로컬 전용 모드에서는 courseId가 없어 docs/gpx-export.md의 `{courseId}.gpx` 규칙을
// 그대로 따를 수 없습니다. 백엔드 연동 후 저장된 코스를 내보낼 때는 courseId 기반
// 파일명으로 교체하세요 (mobile/WORKLOG.md 참고).
//
// `expo-file-system/legacy`의 writeAsStringAsync는 SDK 54부터 호출 시 즉시 에러를
// 던지므로(legacyWarnings) 사용하지 않습니다. 대신 새 File/Paths 클래스 API를 사용합니다
// (running-app/src/components/ExportButtons.tsx에서 이미 검증된 패턴 — mobile/WORKLOG.md 참고).
export async function exportGpx(points: RoutePoint[], routeName = 'Runvas Route'): Promise<void> {
  const gpxContent = buildGpxString(points, routeName);
  const fileName = `runvas-route-${Date.now()}.gpx`;

  const file = new File(Paths.cache, fileName);
  file.write(gpxContent);

  const canShare = await Sharing.isAvailableAsync();
  if (!canShare) {
    throw new Error('이 기기에서는 파일 공유가 지원되지 않습니다.');
  }

  await Sharing.shareAsync(file.uri, {
    mimeType: 'application/gpx+xml',
    dialogTitle: 'GPX 파일 저장',
    UTI: 'com.topografix.gpx',
  });
}
