package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;

public final class ParkOperatingHours {
    private ParkOperatingHours() {}

    public static int openingHour(LocalDate date) {
        if (date == null) throw new ApiException("Visit date is required");
        if (Boolean.parseBoolean(System.getenv("SIKKA_PARK_UNRESTRICTED_HOURS"))) return 0;
        String closed = System.getenv("SIKKA_PARK_CLOSED_DATES");
        if (closed != null && Arrays.asList(closed.split(",")).contains(date.toString())) throw new ApiException("The park is closed on the selected date");
        String opened = System.getenv("SIKKA_PARK_EXTRA_OPEN_DATES");
        boolean exception = opened != null && Arrays.asList(opened.split(",")).contains(date.toString());
        if (!exception && (date.getDayOfWeek() == DayOfWeek.MONDAY || date.getDayOfWeek() == DayOfWeek.TUESDAY)) throw new ApiException("The park is closed on Mondays and Tuesdays");
        return date.getDayOfWeek() == DayOfWeek.SATURDAY ? 12 : 16;
    }

    public static void validateVisitWindow(LocalDate date, LocalTime arrival, Integer duration) {
        int opening = openingHour(date);
        if (duration == null || duration <= 0) throw new ApiException("Visit duration must be positive");
        if (arrival == null || Boolean.parseBoolean(System.getenv("SIKKA_PARK_UNRESTRICTED_HOURS"))) return;
        int minute = arrival.getHour() * 60 + arrival.getMinute();
        if (minute < opening * 60 || duration == null || duration <= 0 || minute + duration > 1440) throw new ApiException("Choose an arrival and duration within park opening hours");
    }
}
