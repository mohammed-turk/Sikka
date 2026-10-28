package com.example.sikka.Service;

import com.example.sikka.Model.Ride;
import com.example.sikka.Repository.RideRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RidePlanningDefaultsTest {
    @Mock private RideRepository rides;
    @InjectMocks private RideService service;

    @Test
    void missingWaitUsesAnExplicitEstimateWithoutChangingObservedDataOrClosure() {
        Ride ride = new Ride();
        ride.setId(1);
        ride.setStatus("CLOSED");
        when(rides.findRideById(1)).thenReturn(ride);
        Map<String, Object> snapshot = service.getRideSnapshot(1);
        assertEquals(15, snapshot.get("planningWaitMinutes"));
        assertEquals("SIKKA_DEFAULT_ESTIMATE", snapshot.get("waitSource"));
        assertEquals(true, snapshot.get("waitIsEstimate"));
        assertEquals("CLOSED", snapshot.get("status"));
        assertNull(ride.getWaitMinutes());
        assertNull(ride.getWaitObservedAt());
        verify(rides, never()).save(any());
    }

    @Test
    void recentSourceWaitOverridesTheDefaultIncludingZero() {
        Ride ride = new Ride();
        ride.setId(1);
        ride.setWaitMinutes(0);
        ride.setWaitObservedAt(LocalDateTime.now().minusMinutes(1));
        ride.setWaitSource("VERIFIED_SOURCE");
        when(rides.findRideById(1)).thenReturn(ride);
        Map<String, Object> snapshot = service.getRideSnapshot(1);
        assertEquals(0, snapshot.get("planningWaitMinutes"));
        assertEquals("VERIFIED_SOURCE", snapshot.get("waitSource"));
    }

    @Test
    void fillMissingDurationsOnceAndPreserveReviewedDurationsAndStatus() {
        Ride missing = new Ride();
        missing.setStatus("UNAVAILABLE");
        Ride reviewed = new Ride();
        reviewed.setRideDurationMinutes(9);
        reviewed.setEntryExitBufferMinutes(0);
        when(rides.findAll()).thenReturn(List.of(missing, reviewed));
        RidePlanningDefaultsService defaults = new RidePlanningDefaultsService(rides);
        defaults.fillMissingPlanningDurations();
        defaults.fillMissingPlanningDurations();
        assertEquals(5, missing.getRideDurationMinutes());
        assertEquals(3, missing.getEntryExitBufferMinutes());
        assertEquals("SIKKA_DEFAULT_ESTIMATE", missing.getTimingSource());
        assertEquals("UNAVAILABLE", missing.getStatus());
        assertEquals(9, reviewed.getRideDurationMinutes());
        assertEquals(0, reviewed.getEntryExitBufferMinutes());
        verify(rides, times(1)).save(missing);
        verify(rides, never()).save(reviewed);
    }
}
