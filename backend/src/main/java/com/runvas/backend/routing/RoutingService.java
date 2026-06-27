package com.runvas.backend.routing;

import com.runvas.backend.common.GeoPoint;
import com.runvas.backend.common.RoutePoint;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

// T-Map 무료 한도(1,000회/일)를 아끼기 위해 출발/도착 좌표 쌍 단위로 결과를 Redis에 캐싱한다.
// 좌표를 소수 4자리(약 11m 격자)로 반올림해 키를 만들어, 거의 같은 지점을 다시 요청해도
// 캐시 히트가 나게 한다 (cacheKey() 주석 참고).
// @Cacheable 대신 CacheManager를 직접 써서 히트/미스를 로그로 명확히 구분한다
// (@Cacheable은 히트 시 메서드 진입 자체를 건너뛰어 내부에서 로그를 찍을 수 없다).
@Service
public class RoutingService {

	private static final Logger log = LoggerFactory.getLogger(RoutingService.class);
	private static final String CACHE_NAME = "pedestrianRoutes";

	private final TmapPedestrianClient tmapPedestrianClient;
	private final CacheManager cacheManager;

	public RoutingService(TmapPedestrianClient tmapPedestrianClient, CacheManager cacheManager) {
		this.tmapPedestrianClient = tmapPedestrianClient;
		this.cacheManager = cacheManager;
	}

	@SuppressWarnings("unchecked")
	public List<RoutePoint> findPedestrianRoute(GeoPoint start, GeoPoint end) {
		String key = cacheKey(start, end);
		Cache cache = cacheManager.getCache(CACHE_NAME);

		List<RoutePoint> cached = cache.get(key, List.class);
		if (cached != null) {
			log.info("캐시 히트: key={}", key);
			return cached;
		}

		log.info("캐시 미스: key={}, T-Map 호출", key);
		List<RoutePoint> path = tmapPedestrianClient.fetchRoute(start, end);
		cache.put(key, path);
		return path;
	}

	// 소수 5자리(약 1.1m 격자)로는 실사용 탭 좌표 차이(2~3m)도 흡수하지 못해 캐시 미스가 잦았다.
	// 4자리(약 11m 격자)로 넓혀 "거의 같은 지점"을 같은 캐시 키로 묶는다.
	public String cacheKey(GeoPoint start, GeoPoint end) {
		return String.format(
				Locale.ROOT,
				"%.4f,%.4f|%.4f,%.4f",
				start.latitude(),
				start.longitude(),
				end.latitude(),
				end.longitude());
	}
}
