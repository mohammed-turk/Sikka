package com.example.sikka.Repository;

import com.example.sikka.Model.RidePreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RidePreferenceRepository extends JpaRepository<RidePreference, Integer> {

    RidePreference findRidePreferenceById(Integer id);

    List<RidePreference> findRidePreferencesByVisitorProfileId(Integer visitorProfileId);

    boolean existsByVisitorProfileIdAndRideId(Integer visitorProfileId, Integer rideId);

}