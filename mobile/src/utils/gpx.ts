import { RoutePoint } from '../types';

// docs/gpx-export.md: GPX 1.1, UTF-8, WGS84. <trkpt>는 lat/lon만 포함하고
// 시간·고도·심박수·케이던스·속도는 MVP 범위에서 제외합니다.
export function buildGpxString(points: RoutePoint[], routeName = 'RunSketch Route'): string {
  const trackPoints = [...points]
    .sort((a, b) => a.sequence - b.sequence)
    .map(({ latitude, longitude }) => `    <trkpt lat="${latitude}" lon="${longitude}"></trkpt>`)
    .join('\n');

  return `<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="RunSketch"
  xmlns="http://www.topografix.com/GPX/1/1"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://www.topografix.com/GPX/1/1
    http://www.topografix.com/GPX/1/1/gpx.xsd">
  <trk>
    <name>${routeName}</name>
    <trkseg>
${trackPoints}
    </trkseg>
  </trk>
</gpx>`;
}
