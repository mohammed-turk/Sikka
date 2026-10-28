package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalTime;
import static org.junit.jupiter.api.Assertions.*;

class ParkOperatingHoursTest {
    @Test
    void weeklyScheduleAndClosingTimeAreEnforced() {
        assertThrows(ApiException.class, () -> ParkOperatingHours.openingHour(LocalDate.of(2026, 10, 26)));
        assertThrows(ApiException.class, () -> ParkOperatingHours.openingHour(LocalDate.of(2026, 10, 27)));
        assertEquals(16, ParkOperatingHours.openingHour(LocalDate.of(2026, 10, 28)));
        assertEquals(12, ParkOperatingHours.openingHour(LocalDate.of(2026, 10, 31)));
        assertDoesNotThrow(() -> ParkOperatingHours.validateVisitWindow(LocalDate.of(2026, 10, 31), LocalTime.NOON, 720));
        assertThrows(ApiException.class, () -> ParkOperatingHours.validateVisitWindow(LocalDate.of(2026, 10, 28), LocalTime.of(15, 0), 60));
        assertThrows(ApiException.class, () -> ParkOperatingHours.validateVisitWindow(LocalDate.of(2026, 10, 28), LocalTime.of(23, 0), 61));
    }
}
