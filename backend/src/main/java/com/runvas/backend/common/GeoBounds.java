package com.runvas.backend.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record GeoBounds(@Valid @NotNull GeoPoint southWest, @Valid @NotNull GeoPoint northEast) {
}
