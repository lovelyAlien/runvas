package com.runvas.backend.admin;

import java.time.LocalDate;

public record DailyTrendPoint(LocalDate day, long count) {
}
