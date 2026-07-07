package com.runvas.backend.routing;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TmapReverseGeocodingClient {

	private static final Logger log = LoggerFactory.getLogger(TmapReverseGeocodingClient.class);
	private static final String TMAP_URL = "https://apis.openapi.sk.com/tmap/geo/reversegeocoding";

	private final RestClient restClient = RestClient.create();
	private final String appKey;

	public TmapReverseGeocodingClient(@Value("${runvas.tmap.app-key}") String appKey) {
		this.appKey = appKey;
	}

	public String fetchAddress(double latitude, double longitude) {
		try {
			Map<?, ?> response = restClient.get()
					.uri(TMAP_URL + "?version=1&lat={lat}&lon={lon}&coordType=WGS84GEO&addressType=A10&appKey={appKey}",
							latitude, longitude, appKey)
					.retrieve()
					.body(Map.class);

			if (response == null) return null;

			Map<?, ?> addressInfo = (Map<?, ?>) response.get("addressInfo");
			if (addressInfo == null) return null;

			String fullAddress = (String) addressInfo.get("fullAddress");
			return (fullAddress != null && !fullAddress.isBlank()) ? fullAddress : null;
		} catch (RestClientException ex) {
			log.warn("T-Map 역지오코딩 실패: lat={}, lon={}", latitude, longitude, ex);
			return null;
		}
	}
}
