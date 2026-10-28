package com.example.sikka.Controller;

import com.example.sikka.Api.ApiResponse;
import com.example.sikka.DtoIn.PlanItemDtoIn;
import com.example.sikka.Service.PlanItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.example.sikka.Model.Account;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/plan-item")
public class PlanItemController {

    private final PlanItemService planItemService;

    @GetMapping("/get/items/{visitPlanId}")
    public ResponseEntity<?> getPlanItems(@AuthenticationPrincipal Account account, @PathVariable Integer visitPlanId) {
        return ResponseEntity.status(200).body(planItemService.getPlanItems(account.getId(), visitPlanId));
    }

    @GetMapping("/get/details/{planItemId}")
    public ResponseEntity<?> getPlanItemDetails(@AuthenticationPrincipal Account account, @PathVariable Integer planItemId) {
        return ResponseEntity.status(200).body(planItemService.getPlanItemDetails(account.getId(), planItemId));
    }

    @PutMapping("/update/preferences")
    public ResponseEntity<?> updatePlanItemPreferences(@AuthenticationPrincipal Account account, @RequestBody @Valid PlanItemDtoIn planItemDtoIn) {
        planItemService.updatePlanItemPreferences(account.getId(), planItemDtoIn);
        return ResponseEntity.status(200).body(new ApiResponse("Plan item preferences updated successfully"));
    }
}
