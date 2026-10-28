package com.example.sikka.Service;

import com.example.sikka.Model.Ride;
import com.example.sikka.Model.Place;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class PlanningAvailability {
    private PlanningAvailability() {}

    public static String rideStatus(Ride ride, LocalDate visitDate) {
        String source = ride.getAvailabilitySource();
        if ("SIKKA_DEFAULT_AVAILABILITY".equals(source)) return "UNKNOWN";
        if ("QUEUE_ANALYTICS_LIVE".equals(source) && (visitDate != null && !visitDate.equals(LocalDate.now()) || ride.getWaitObservedAt() == null || ride.getWaitObservedAt().isBefore(LocalDateTime.now().minusMinutes(10)) || ride.getWaitObservedAt().isAfter(LocalDateTime.now().plusSeconds(30)))) return "UNKNOWN";
        if ("OPEN".equals(ride.getStatus())) return "OPEN";
        if ("CLOSED".equals(ride.getStatus()) || "UNAVAILABLE".equals(ride.getStatus()) && "ADMIN".equals(source)) return "CLOSED";
        return "UNKNOWN";
    }

    public static boolean canPlanRide(Ride ride, LocalDate visitDate) {
        return ride != null && !"CLOSED".equals(rideStatus(ride, visitDate));
    }

    public static boolean canPlanPlace(Place place) {
        return place != null && ("OPEN".equals(place.getStatus()) || "UNAVAILABLE".equals(place.getStatus()) && !"ADMIN".equals(place.getAvailabilitySource()));
    }
}
