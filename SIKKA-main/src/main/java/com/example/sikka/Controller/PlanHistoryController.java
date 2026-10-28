package com.example.sikka.Controller;

import com.example.sikka.DtoOut.PlanHistoryDtoOut;
import com.example.sikka.Service.PlanHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.example.sikka.Model.Account;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/plan-history")
public class PlanHistoryController {

    private final PlanHistoryService planHistoryService;

    @GetMapping("/my-history")
    public ResponseEntity<List<PlanHistoryDtoOut>> getMyPlanHistory(@AuthenticationPrincipal Account account) {
        return ResponseEntity.status(200).body(planHistoryService.getMyPlanHistory(account.getId()));
    }

    @GetMapping("/details/{planHistoryId}")
    public ResponseEntity<PlanHistoryDtoOut> getPlanHistoryDetails(@AuthenticationPrincipal Account account, @PathVariable Integer planHistoryId) {
        return ResponseEntity.status(200).body(planHistoryService.getPlanHistoryDetails(account.getId(), planHistoryId));
    }
}