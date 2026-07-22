package com.runvas.backend.admin;

import java.time.LocalDate;

public interface DailyCountProjection {

    LocalDate getDay();

    long getCnt();
}
