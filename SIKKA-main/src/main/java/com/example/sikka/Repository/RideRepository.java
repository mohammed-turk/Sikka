package com.example.sikka.Repository;

import com.example.sikka.Model.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RideRepository extends JpaRepository<Ride, Integer> {

    Ride findRideById(Integer id);

    List<Ride> findRidesByStatus(String status);

    @Query("select r from Ride r where (?1 is null or r.category = ?1) and (?2 is null or r.thrillLevel = ?2) and (?3 is null or r.status = ?3)")
    List<Ride> filterRides(String category, String thrill, String status);

    Ride findRideBySourceRideId(String sourceRideId);

    boolean existsByNameIgnoreCase(String name);
}