package com.runvas.backend.routing.dto;

import com.runvas.backend.common.GeoPoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// docs/api-contract.md POST /routes/pedestrian 요청 본문.
public record PedestrianRouteRequest(@NotNull @Valid GeoPoint start, @NotNull @Valid GeoPoint end) {
}
