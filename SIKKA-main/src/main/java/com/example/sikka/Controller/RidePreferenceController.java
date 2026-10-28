package com.example.sikka.Controller;

import com.example.sikka.Api.ApiResponse;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.RidePreference;
import com.example.sikka.Service.RidePreferenceService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ride-preference")
@AllArgsConstructor
public class RidePreferenceController {

    private final RidePreferenceService ridePreferenceService;

    @GetMapping("/get/my/preferences")
    public ResponseEntity<?> getMyRidePreferences(@AuthenticationPrincipal Account account){
        return ResponseEntity.status(200).body(ridePreferenceService.getMyRidePreferences(account.getId()));
    }

    @GetMapping("/get/details/{preferenceId}")
    public ResponseEntity<?> getRidePreferenceById(@AuthenticationPrincipal Account account, @PathVariable Integer preferenceId){
        return ResponseEntity.status(200).body(ridePreferenceService.getRidePreferenceById(account.getId(), preferenceId));
    }

    @PostMapping("/add/{rideId}")
    public ResponseEntity<?> addRidePreference(@AuthenticationPrincipal Account account, @PathVariable Integer rideId, @RequestBody @Valid RidePreference ridePreference){
        ridePreferenceService.addRidePreference(account.getId(), rideId, ridePreference);
        return ResponseEntity.status(200).body(new ApiResponse("Ride preference added successfully"));
    }

    @PutMapping("/update/{preferenceId}")
    public ResponseEntity<?> updateRidePreference(@AuthenticationPrincipal Account account, @PathVariable Integer preferenceId, @RequestBody @Valid RidePreference ridePreference){
        ridePreferenceService.updateRidePreference(account.getId(), preferenceId, ridePreference);
        return ResponseEntity.status(200).body(new ApiResponse("Ride preference updated successfully"));
    }

    @DeleteMapping("/delete/{preferenceId}")
    public ResponseEntity<?> deleteRidePreference(@AuthenticationPrincipal Account account, @PathVariable Integer preferenceId){
        ridePreferenceService.deleteRidePreference(account.getId(), preferenceId);
        return ResponseEntity.status(200).body(new ApiResponse("Ride preference deleted successfully"));
    }
}