package com.example.sikka.Service;

import com.example.sikka.Model.Ride;
import com.example.sikka.Repository.RideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RidePlanningDefaultsService {
    private final RideRepository rideRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void fillMissingPlanningDurations() {
        for (Ride ride : rideRepository.findAll()) {
            boolean changed = false;
            if (ride.getRideDurationMinutes() == null) {
                ride.setRideDurationMinutes(5);
                changed = true;
            }
            if (ride.getEntryExitBufferMinutes() == null) {
                ride.setEntryExitBufferMinutes(3);
                changed = true;
            }
            if (changed) {
                ride.setTimingSource("SIKKA_DEFAULT_ESTIMATE");
                rideRepository.save(ride);
            }
        }
    }
}
