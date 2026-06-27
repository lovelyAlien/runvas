package com.runvas.backend.course;

import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.common.GeoBounds;
import com.runvas.backend.common.RoutePoint;
import java.util.List;
import org.springframework.stereotype.Component;

// docs/data-model.md Course 제한값 + RoutePoint 순서 규칙, docs/geo-conventions.md Bounds 정의.
@Component
public class CourseValidator {

	private static final int MIN_POINTS = 2;
	private static final int MAX_POINTS = 5000;
	private static final int MIN_DISTANCE_METERS = 100;
	private static final int MAX_DISTANCE_METERS = 100_000;

	public void validate(List<RoutePoint> path, int distanceMeters, GeoBounds bounds) {
		validatePointCount(path);
		validateSequenceContinuity(path);
		validateDistance(distanceMeters);
		validateBoundsContainsPath(path, bounds);
	}

	private void validatePointCount(List<RoutePoint> path) {
		if (path.size() < MIN_POINTS || path.size() > MAX_POINTS) {
			throw new ApiException(
					ErrorCode.VALIDATION_ERROR,
					"Path must contain " + MIN_POINTS + "-" + MAX_POINTS + " points",
					List.of(new ApiException.FieldErrorDetail("path", "포인트 개수가 범위를 벗어났습니다")));
		}
	}

	private void validateSequenceContinuity(List<RoutePoint> path) {
		for (int i = 0; i < path.size(); i++) {
			if (path.get(i).sequence() == null || path.get(i).sequence() != i) {
				throw new ApiException(
						ErrorCode.VALIDATION_ERROR,
						"Path sequence must be continuous starting from 0",
						List.of(new ApiException.FieldErrorDetail("path", "sequence가 연속적이지 않습니다")));
			}
		}
	}

	private void validateDistance(int distanceMeters) {
		if (distanceMeters < MIN_DISTANCE_METERS || distanceMeters > MAX_DISTANCE_METERS) {
			throw new ApiException(
					ErrorCode.VALIDATION_ERROR,
					"distanceMeters must be between " + MIN_DISTANCE_METERS + " and " + MAX_DISTANCE_METERS,
					List.of(new ApiException.FieldErrorDetail("distanceMeters", "거리 제한을 벗어났습니다")));
		}
	}

	private void validateBoundsContainsPath(List<RoutePoint> path, GeoBounds bounds) {
		double swLat = bounds.southWest().latitude();
		double swLng = bounds.southWest().longitude();
		double neLat = bounds.northEast().latitude();
		double neLng = bounds.northEast().longitude();

		boolean allContained = path.stream().allMatch(point ->
				point.latitude() >= swLat && point.latitude() <= neLat
						&& point.longitude() >= swLng && point.longitude() <= neLng);

		if (!allContained) {
			throw new ApiException(
					ErrorCode.VALIDATION_ERROR,
					"bounds must contain every point in path",
					List.of(new ApiException.FieldErrorDetail("bounds", "path 전체를 포함하지 않습니다")));
		}
	}
}
