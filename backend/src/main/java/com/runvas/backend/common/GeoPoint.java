package com.runvas.backend.common;

import jakarta.validation.constraints.NotNull;

// docs/geo-conventions.md: 좌표 필드명은 항상 latitude/longitude, lat/lng 축약 금지.
public record GeoPoint(@NotNull Double latitude, @NotNull Double longitude) {
}
