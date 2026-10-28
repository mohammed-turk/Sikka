package com.example.sikka.Controller;

import com.example.sikka.Api.ApiResponse;
import com.example.sikka.Model.Place;
import com.example.sikka.Service.PlaceService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/place")
@AllArgsConstructor
public class PlaceController {

    private final PlaceService placeService;

    @GetMapping("/get")
    public ResponseEntity<?> getPlaces(){
        return ResponseEntity.status(200).body(placeService.getPlaces());
    }

    @GetMapping("/get/{placeId}")
    public ResponseEntity<?> getPlaceById(@PathVariable Integer placeId){
        return ResponseEntity.status(200).body(placeService.getPlaceById(placeId));
    }

    @PostMapping("/add")
    public ResponseEntity<?> addPlace(@RequestBody @Valid Place place){
        placeService.addPlace(place);
        return ResponseEntity.status(200).body(new ApiResponse("Place added successfully"));
    }

    @PutMapping("/update/{placeId}")
    public ResponseEntity<?> updatePlace(@PathVariable Integer placeId, @RequestBody @Valid Place place){
        placeService.updatePlace(placeId, place);
        return ResponseEntity.status(200).body(new ApiResponse("Place updated successfully"));
    }

    @DeleteMapping("/delete/{placeId}")
    public ResponseEntity<?> deletePlace(@PathVariable Integer placeId){
        placeService.deletePlace(placeId);
        return ResponseEntity.status(200).body(new ApiResponse("Place deleted successfully"));
    }

    @GetMapping("/by-kind")
    public ResponseEntity<?> getPlacesByKind(@RequestParam String kind){
        return ResponseEntity.status(200).body(placeService.getPlaces(kind));
    }
}