package com.example.sikka.Controller;

import com.example.sikka.Api.ApiResponse;
import com.example.sikka.DtoIn.MealPreferencesDtoIn;
import com.example.sikka.DtoIn.SelectMealRestaurantDtoIn;
import com.example.sikka.DtoIn.SharedMealBreakDtoIn;
import com.example.sikka.DtoIn.VisitMealBreakDtoIn;
import com.example.sikka.Model.Account;
import com.example.sikka.Service.VisitMealBreakService;
import com.example.sikka.Service.VisitPlanService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/visit-meal-break")
@AllArgsConstructor
public class VisitMealBreakController {

    private final VisitMealBreakService visitMealBreakService;
    private final VisitPlanService visitPlanService;

    @GetMapping("/get/visit/{visitId}")
    public ResponseEntity<?> getVisitMealBreaks(@AuthenticationPrincipal Account account, @PathVariable Integer visitId){
        return ResponseEntity.status(200).body(visitMealBreakService.getVisitMealBreaks(account.getId(), visitId));
    }

    @PostMapping("/add/{visitId}/{visitMemberId}")
    public ResponseEntity<?> addVisitMealBreak(@AuthenticationPrincipal Account account, @PathVariable Integer visitId, @PathVariable Integer visitMemberId, @RequestBody @Valid VisitMealBreakDtoIn dto){
        visitMealBreakService.addVisitMealBreak(account.getId(), visitId, visitMemberId, dto);
        return ResponseEntity.status(200).body(new ApiResponse("Visit meal break added successfully"));
    }

    @PutMapping("/update/{visitId}/{mealBreakId}")
    public ResponseEntity<?> updateVisitMealBreak(@AuthenticationPrincipal Account account, @PathVariable Integer visitId, @PathVariable Integer mealBreakId, @RequestBody @Valid VisitMealBreakDtoIn dto){
        visitMealBreakService.updateVisitMealBreak(account.getId(), visitId, mealBreakId, dto);
        return ResponseEntity.status(200).body(new ApiResponse("Visit meal break updated successfully"));
    }

    @DeleteMapping("/delete/{visitId}/{mealBreakId}")
    public ResponseEntity<?> deleteVisitMealBreak(@AuthenticationPrincipal Account account, @PathVariable Integer visitId, @PathVariable Integer mealBreakId){
        visitMealBreakService.deleteVisitMealBreak(account.getId(), visitId, mealBreakId);
        return ResponseEntity.status(200).body(new ApiResponse("Visit meal break deleted successfully"));
    }

    @GetMapping("/get/my/{visitId}")
    public ResponseEntity<?> getMyMealBreaks(@AuthenticationPrincipal Account account, @PathVariable Integer visitId){
        return ResponseEntity.status(200).body(visitMealBreakService.getMyMealBreaks(account.getId(), visitId));
    }

    @PostMapping("/add/shared/{visitId}")
    public ResponseEntity<?> addSharedMealBreak(@AuthenticationPrincipal Account account, @PathVariable Integer visitId, @RequestBody @Valid SharedMealBreakDtoIn dto){
        visitMealBreakService.addSharedMealBreak(account.getId(), visitId, dto);
        return ResponseEntity.status(200).body(new ApiResponse("Shared meal break added successfully"));
    }

    @DeleteMapping("/delete/shared/{visitId}/{breakKey}")
    public ResponseEntity<?> deleteSharedMealBreak(@AuthenticationPrincipal Account account, @PathVariable Integer visitId, @PathVariable String breakKey){
        visitMealBreakService.deleteSharedMealBreak(account.getId(), visitId, breakKey);
        return ResponseEntity.status(200).body(new ApiResponse("Shared meal break deleted successfully"));
    }

    @PutMapping("/update/shared/{visitId}/{breakKey}")
    public ResponseEntity<?> updateSharedMealBreak(@AuthenticationPrincipal Account account, @PathVariable Integer visitId, @PathVariable String breakKey, @RequestBody @Valid VisitMealBreakDtoIn dto){
        visitMealBreakService.updateSharedMealBreak(account.getId(), visitId, breakKey, dto);
        return ResponseEntity.status(200).body(new ApiResponse("Shared meal break updated successfully"));
    }

    @PutMapping("/preferences/{visitId}")
    public ResponseEntity<?> saveMealPreferences(@AuthenticationPrincipal Account account, @PathVariable Integer visitId, @RequestBody @Valid MealPreferencesDtoIn dto){
        visitMealBreakService.saveMealPreferences(account.getId(), visitId, dto);
        return ResponseEntity.status(200).body(new ApiResponse("Meal preferences saved successfully"));
    }

    @PutMapping("/restaurant/{visitId}/{breakKey}")
    public ResponseEntity<?> selectMealRestaurant(@AuthenticationPrincipal Account account, @PathVariable Integer visitId, @PathVariable String breakKey, @RequestBody @Valid SelectMealRestaurantDtoIn dto){
        visitPlanService.selectMealRestaurant(account.getId(), visitId, breakKey, dto);
        return ResponseEntity.status(200).body(new ApiResponse("Restaurant selected and walking times updated successfully"));
    }
}