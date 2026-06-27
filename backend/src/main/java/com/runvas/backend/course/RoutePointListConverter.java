package com.runvas.backend.course;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runvas.backend.common.RoutePoint;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

// path 전체를 JSON 컬럼에 저장한다 (docs/data-model.md: 수정 시 path는 항상 전체 교체라서
// 포인트 단위 쿼리가 필요 없다 — 정규화 테이블 대신 JSON 컬럼을 선택한 이유는
// backend/docs/design.md 참고).
@Converter
public class RoutePointListConverter implements AttributeConverter<List<RoutePoint>, String> {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Override
	public String convertToDatabaseColumn(List<RoutePoint> attribute) {
		try {
			return OBJECT_MAPPER.writeValueAsString(attribute);
		} catch (Exception ex) {
			throw new IllegalStateException("RoutePoint 목록을 직렬화할 수 없습니다", ex);
		}
	}

	@Override
	public List<RoutePoint> convertToEntityAttribute(String dbData) {
		try {
			return OBJECT_MAPPER.readValue(dbData, new TypeReference<List<RoutePoint>>() {});
		} catch (Exception ex) {
			throw new IllegalStateException("RoutePoint 목록을 역직렬화할 수 없습니다", ex);
		}
	}
}
