package com.runvas.backend.routing;

import com.runvas.backend.common.RoutePoint;
import com.runvas.backend.routing.dto.PedestrianRouteRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RoutingController {

	private final RoutingService routingService;

	@PostMapping("/pedestrian")
	public Map<String, List<RoutePoint>> findPedestrianRoute(@Valid @RequestBody PedestrianRouteRequest request) {
		List<RoutePoint> path = routingService.findPedestrianRoute(request.start(), request.end());
		return Map.of("path", path);
	}
}
