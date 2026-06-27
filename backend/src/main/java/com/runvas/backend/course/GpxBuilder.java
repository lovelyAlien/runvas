package com.runvas.backend.course;

import com.runvas.backend.common.RoutePoint;
import java.util.List;

// docs/api-contract.md GET /courses/{id}/gpx — Content-Type application/gpx+xml.
public final class GpxBuilder {

	private GpxBuilder() {}

	public static String build(String courseName, List<RoutePoint> path) {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<gpx version=\"1.1\" creator=\"Runvas\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
		sb.append("  <trk>\n    <name>").append(escapeXml(courseName)).append("</name>\n    <trkseg>\n");
		for (RoutePoint point : path) {
			sb.append("      <trkpt lat=\"")
					.append(point.latitude())
					.append("\" lon=\"")
					.append(point.longitude())
					.append("\" />\n");
		}
		sb.append("    </trkseg>\n  </trk>\n</gpx>\n");
		return sb.toString();
	}

	private static String escapeXml(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
