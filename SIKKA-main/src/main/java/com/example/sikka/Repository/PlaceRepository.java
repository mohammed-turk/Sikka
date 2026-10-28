package com.example.sikka.Repository;

import com.example.sikka.Model.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaceRepository extends JpaRepository<Place, Integer> {

    Place findPlaceById(Integer id);

    List<Place> findPlacesByKind(String kind);

    Place findPlaceBySourcePlaceId(String sourcePlaceId);

    boolean existsByNameIgnoreCase(String name);
}