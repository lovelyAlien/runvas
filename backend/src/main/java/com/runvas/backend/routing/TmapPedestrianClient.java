package com.runvas.backend.routing;

import com.runvas.backend.common.GeoPoint;
import com.runvas.backend.common.RoutePoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// T-Map 보행자 경로 탐색 API 호출. 모바일이 직접 호출하던 mobile/src/utils/tmapRouting.ts를
// 그대로 포팅 — 키가 클라이언트에 노출되지 않도록 백엔드에서만 호출한다. 실패 시 직선 2점으로
// 폴백해 RoutingService가 항상 경로를 반환할 수 있게 한다.
@Component
public class TmapPedestrianClient {

	private static final Logger log = LoggerFactory.getLogger(TmapPedestrianClient.class);
	private static final String TMAP_URL = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1";

	private final RestClient restClient = RestClient.create();
	private final String appKey;

	public TmapPedestrianClient(@Value("${runvas.tmap.app-key}") String appKey) {
		this.appKey = appKey;
	}

	public List<RoutePoint> fetchRoute(GeoPoint start, GeoPoint end) {
		try {
			Map<String, Object> body = Map.of(
					"startX", String.valueOf(start.longitude()),
					"startY", String.valueOf(start.latitude()),
					"endX", String.valueOf(end.longitude()),
					"endY", String.valueOf(end.latitude()),
					"startName", "출발지",
					"endName", "도착지",
					"reqCoordType", "WGS84GEO",
					"resCoordType", "WGS84GEO");

			Map<String, Object> response = restClient.post()
					.uri(TMAP_URL)
					.header("appKey", appKey)
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(Map.class);

			List<RoutePoint> path = parseFeatureCollection(response);
			if (path.isEmpty()) {
				log.warn("T-Map 응답에 유효한 경로 없음, 직선 폴백: start={}, end={}, rawResponse={}",
						start, end, response);
				return straightLine(start, end);
			}
			log.debug("T-Map 경로 {}개 포인트 수신: start={}, end={}", path.size(), start, end);
			return path;
		} catch (RestClientException ex) {
			log.warn("T-Map 호출 실패, 직선 폴백: start={}, end={}", start, end, ex);
			return straightLine(start, end);
		}
	}

	@SuppressWarnings("unchecked")
	private List<RoutePoint> parseFeatureCollection(Map<String, Object> response) {
		List<RoutePoint> result = new ArrayList<>();
		if (response == null) {
			return result;
		}
		List<Map<String, Object>> features = (List<Map<String, Object>>) response.get("features");
		if (features == null) {
			return result;
		}
		int sequence = 0;
		for (Map<String, Object> feature : features) {
			Map<String, Object> geometry = (Map<String, Object>) feature.get("geometry");
			if (geometry == null || !"LineString".equals(geometry.get("type"))) {
				continue;
			}
			List<List<Double>> coordinates = (List<List<Double>>) geometry.get("coordinates");
			for (List<Double> coordinate : coordinates) {
				result.add(new RoutePoint(coordinate.get(1), coordinate.get(0), sequence++));
			}
		}
		return result;
	}

	private List<RoutePoint> straightLine(GeoPoint start, GeoPoint end) {
		return List.of(
				new RoutePoint(start.latitude(), start.longitude(), 0),
				new RoutePoint(end.latitude(), end.longitude(), 1));
	}
}
