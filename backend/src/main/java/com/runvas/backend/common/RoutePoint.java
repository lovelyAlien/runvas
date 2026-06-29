package com.runvas.backend.common;

// docs/data-model.md RoutePoint와 1:1. sequence는 0부터 연속이어야 한다 (CourseValidator에서 검증).
public record RoutePoint(Double latitude, Double longitude, Integer sequence) {
}
