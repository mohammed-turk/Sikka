package com.example.sikka.Repository;

import com.example.sikka.Model.Ride;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.test.database.replace=none")
class RideRepositoryTest {

    @Autowired
    RideRepository rideRepository;

    Ride openRide, closedRide;

    @BeforeEach
    void setUp() {
        openRide = new Ride();
        openRide.setName("Test Coaster");
        openRide.setSourceRideId("test-coaster");
        openRide.setCategory("THRILL");
        openRide.setThrillLevel("EXTREME");
        openRide.setStatus("OPEN");

        closedRide = new Ride();
        closedRide.setName("Test Carousel");
        closedRide.setSourceRideId("test-carousel");
        closedRide.setCategory("FAMILY");
        closedRide.setThrillLevel("LOW");
        closedRide.setStatus("CLOSED");

        rideRepository.save(openRide);
        rideRepository.save(closedRide);
    }

    @Test
    void findRidesByStatusTest() {
        List<Ride> rides = rideRepository.findRidesByStatus("OPEN");

        assertThat(rides).hasSize(1);
        assertThat(rides.get(0).getName()).isEqualTo("Test Coaster");
    }

    @Test
    void findRideBySourceRideIdTest() {
        Ride found = rideRepository.findRideBySourceRideId("test-carousel");

        assertThat(found).isNotNull();
        assertThat(found.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void existsByNameIgnoreCaseTest() {
        assertThat(rideRepository.existsByNameIgnoreCase("TEST COASTER")).isTrue();
        assertThat(rideRepository.existsByNameIgnoreCase("Unknown Ride")).isFalse();
    }

    @Test
    void filterRidesTest() {
        List<Ride> thrill = rideRepository.filterRides("THRILL", null, null);
        List<Ride> all = rideRepository.filterRides(null, null, null);

        assertThat(thrill).hasSize(1);
        assertThat(all).hasSize(2);
    }
}