package com.example.sikka.Controller;

import com.example.sikka.Api.ApiResponse;
import com.example.sikka.DtoIn.LinkRideMapDtoIn;
import com.example.sikka.Model.Account;
import com.example.sikka.Service.MapService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/map")
@AllArgsConstructor
public class MapController {

    private final MapService mapService;

    @GetMapping("/get/rides")
    public ResponseEntity<?> getMapRides(){
        return ResponseEntity.status(200).body(mapService.getMapRides());
    }

    @PutMapping("/link/ride/{rideId}")
    public ResponseEntity<?> linkRideToMap(@AuthenticationPrincipal Account account, @PathVariable Integer rideId, @RequestBody @Valid LinkRideMapDtoIn dto){
        mapService.linkRideToMap(account.getId(), rideId, dto);
        return ResponseEntity.status(200).body(new ApiResponse("Ride linked to map successfully"));
    }

    @PostMapping("/import/rides")
    public ResponseEntity<?> importMapRides(@AuthenticationPrincipal Account account){
        int addedCount = mapService.importMapRides(account.getId());
        return ResponseEntity.status(200).body(new ApiResponse(addedCount + " rides imported successfully"));
    }

    @PostMapping("/import/places")
    public ResponseEntity<?> importMapPlaces(@AuthenticationPrincipal Account account){
        int addedCount = mapService.importMapPlaces(account.getId());
        return ResponseEntity.status(200).body(new ApiResponse(addedCount + " places imported successfully"));
    }

    @GetMapping("/get/places")
    public ResponseEntity<?> getMapPlaces(){
        return ResponseEntity.status(200).body(mapService.getMapPlaces());
    }

    @GetMapping("/route/{fromType}/{fromId}/{toType}/{toId}")
    public ResponseEntity<?> getRoute(@PathVariable String fromType, @PathVariable Integer fromId, @PathVariable String toType, @PathVariable Integer toId){
        return ResponseEntity.status(200).body(
                mapService.getRoute(fromType, fromId, toType, toId)
        );
    }

    @GetMapping("/get/data")
    public ResponseEntity<?> getMapData(){
        return ResponseEntity.status(200).body(mapService.getMapData());
    }

    @GetMapping("/get/walking-context")
    public ResponseEntity<?> getWalkingContext(){
        return ResponseEntity.status(200).body(mapService.getWalkingContext());
    }
}