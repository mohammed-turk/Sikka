package com.example.sikka.Controller;

import com.example.sikka.Api.ApiResponse;
import com.example.sikka.DtoIn.PlanApprovalDtoIn;
import com.example.sikka.Service.PlanApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.example.sikka.Model.Account;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/plan-approval")
public class PlanApprovalController {

    private final PlanApprovalService planApprovalService;

    @GetMapping("/get/{visitPlanId}")
    public ResponseEntity<?> getPlanApproval(@AuthenticationPrincipal Account account, @PathVariable Integer visitPlanId) {
        return ResponseEntity.status(200).body(planApprovalService.getPlanApproval(account.getId(), visitPlanId));
    }

    @PostMapping("/create/{visitPlanId}")
    public ResponseEntity<?> createPlanApproval(@AuthenticationPrincipal Account account, @PathVariable Integer visitPlanId) {
        planApprovalService.createPlanApproval(account.getId(), visitPlanId);
        return ResponseEntity.status(201).body(new ApiResponse("Plan approval created successfully"));
    }

    @PutMapping("/approve")
    public ResponseEntity<?> approvePlan(@AuthenticationPrincipal Account account, @RequestBody @Valid PlanApprovalDtoIn dto) {
        planApprovalService.respondToPlanApproval(account.getId(), dto, 1);
        return ResponseEntity.status(200).body(new ApiResponse("Plan approval response saved successfully"));
    }

    @PutMapping("/reject")
    public ResponseEntity<?> rejectPlan(@AuthenticationPrincipal Account account, @RequestBody @Valid PlanApprovalDtoIn dto) {
        planApprovalService.respondToPlanApproval(account.getId(), dto, 2);
        return ResponseEntity.status(200).body(new ApiResponse("Plan approval response saved successfully"));
    }
}
