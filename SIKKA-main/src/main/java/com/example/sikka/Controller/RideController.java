package com.example.sikka.Controller;

import com.example.sikka.Api.ApiResponse;
import com.example.sikka.DtoIn.UpdateRideStatusDtoIn;
import com.example.sikka.DtoIn.UpdateRideWaitDtoIn;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.Ride;
import com.example.sikka.Service.RideService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ride")
@AllArgsConstructor
public class RideController {

    private final RideService rideService;

    @GetMapping("/get")
    public ResponseEntity<?> getRides(){
        return ResponseEntity.status(200).body(rideService.getRides());
    }

    @GetMapping("/get/{rideId}")
    public ResponseEntity<?> getRideById(@PathVariable Integer rideId){
        return ResponseEntity.status(200).body(rideService.getRideById(rideId));
    }

    @PostMapping("/add")
    public ResponseEntity<?> addRide(@RequestBody @Valid Ride ride){
        rideService.addRide(ride);
        return ResponseEntity.status(200).body(new ApiResponse("Ride added successfully"));
    }

    @PutMapping("/update/{rideId}")
    public ResponseEntity<?> updateRide(@PathVariable Integer rideId, @RequestBody @Valid Ride ride){
        rideService.updateRide(rideId, ride);
        return ResponseEntity.status(200).body(new ApiResponse("Ride updated successfully"));
    }

    @DeleteMapping("/delete/{rideId}")
    public ResponseEntity<?> deleteRide(@PathVariable Integer rideId){
        rideService.deleteRide(rideId);
        return ResponseEntity.status(200).body(new ApiResponse("Ride deleted successfully"));
    }

    @GetMapping("/catalog")
    public ResponseEntity<?> getRideCatalog(){
        return ResponseEntity.status(200).body(rideService.getRides());
    }

    @GetMapping("/available")
    public ResponseEntity<?> getOpenRides(){
        return ResponseEntity.status(200).body(rideService.getOpenRides());
    }

    @GetMapping("/wait-times")
    public ResponseEntity<?> getRideWaitTimes(){
        return ResponseEntity.status(200).body(rideService.getRideWaitTimes());
    }

    @GetMapping("/filter")
    public ResponseEntity<?> filterRides(@RequestParam(required = false) String category, @RequestParam(required = false) String thrill, @RequestParam(required = false) String status){
        return ResponseEntity.status(200).body(rideService.filterRides(category, thrill, status));
    }

    @PutMapping("/admin/update-wait/{rideId}")
    public ResponseEntity<?> updateRideWait(@AuthenticationPrincipal Account account, @PathVariable Integer rideId, @RequestBody @Valid UpdateRideWaitDtoIn dto){
        rideService.updateRideWait(account.getId(), rideId, dto);
        return ResponseEntity.status(200).body(new ApiResponse("Ride waiting time updated successfully"));
    }

    @PutMapping("/admin/update-status/{rideId}")
    public ResponseEntity<?> updateRideStatus(@AuthenticationPrincipal Account account, @PathVariable Integer rideId, @RequestBody @Valid UpdateRideStatusDtoIn dto){
        rideService.updateRideStatus(account.getId(), rideId, dto);
        return ResponseEntity.status(200).body(new ApiResponse("Ride status updated successfully"));
    }

    @GetMapping("/eligibility/{memberId}/{rideId}")
    public ResponseEntity<?> checkEligibilityForMember(@AuthenticationPrincipal Account account, @PathVariable Integer memberId, @PathVariable Integer rideId){
        return ResponseEntity.status(200).body(
                rideService.checkEligibilityForMember(account.getId(), memberId, rideId)
        );
    }
}