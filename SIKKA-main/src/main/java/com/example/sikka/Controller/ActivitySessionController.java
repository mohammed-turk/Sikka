package com.example.sikka.Controller;

import com.example.sikka.DtoIn.*;
import com.example.sikka.DtoOut.ActivitySessionDtoOut;
import com.example.sikka.Service.ActivitySessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.example.sikka.Model.Account;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/activity-session")
public class ActivitySessionController {

    private final ActivitySessionService activitySessionService;

    @GetMapping("/history/{visitId}")
    public ResponseEntity<List<ActivitySessionDtoOut>> getMyActivityHistory(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        return ResponseEntity.status(200).body(activitySessionService.getMyActivityHistory(account.getId(), visitId));
    }

    @GetMapping("/current/{visitId}")
    public ResponseEntity<ActivitySessionDtoOut> getMyCurrentActivity(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        return ResponseEntity.status(200).body(activitySessionService.getMyCurrentActivity(account.getId(), visitId));
    }

    @PostMapping("/start/manual/{visitMemberId}/{planItemId}")
    public ResponseEntity<ActivitySessionDtoOut> startManualActivity(@AuthenticationPrincipal Account account, @PathVariable Integer visitMemberId, @PathVariable Integer planItemId) {
        return ResponseEntity.status(200).body(activitySessionService.startManualActivity(account.getId(), visitMemberId, planItemId, null).get(0));
    }

    @PostMapping("/complete/{activitySessionId}")
    public ResponseEntity<ActivitySessionDtoOut> completeActivity(@AuthenticationPrincipal Account account, @PathVariable Integer activitySessionId, @RequestBody @Valid CompleteActivityDtoIn completeActivityDtoIn) {
        return ResponseEntity.status(200).body(activitySessionService.completeActivity(account.getId(), activitySessionId, completeActivityDtoIn, null).get(0));
    }

    @PutMapping("/rate/{activitySessionId}")
    public ResponseEntity<ActivitySessionDtoOut> rateActivity(@AuthenticationPrincipal Account account, @PathVariable Integer activitySessionId, @RequestBody @Valid RateActivityDtoIn rateActivityDtoIn) {
        return ResponseEntity.status(200).body(activitySessionService.rateActivity(account.getId(), activitySessionId, rateActivityDtoIn));
    }

    @PutMapping("/location/{visitId}")
    public ResponseEntity<ActivitySessionDtoOut> updateActivityLocation(@AuthenticationPrincipal Account account, @PathVariable Integer visitId, @RequestBody @Valid UpdateActivityLocationDtoIn dto) {
        return ResponseEntity.status(200).body(activitySessionService.updateActivityLocation(account.getId(), visitId, dto));
    }
    @PostMapping("/start/gps/{visitMemberId}/{planItemId}")
    public ResponseEntity<ActivitySessionDtoOut> startGpsActivity(@AuthenticationPrincipal Account account, @PathVariable Integer visitMemberId, @PathVariable Integer planItemId) {
        return ResponseEntity.status(200).body(activitySessionService.startGpsActivity(account.getId(), visitMemberId, planItemId));
    }

    @PostMapping("/start/manual/family/{planItemId}")
    public ResponseEntity<List<ActivitySessionDtoOut>> startFamilyActivity(@AuthenticationPrincipal Account account, @PathVariable Integer planItemId, @RequestBody @Valid StartFamilyActivityDtoIn dto) {
        return ResponseEntity.status(200).body(activitySessionService.startManualActivity(account.getId(), null, planItemId, dto.getVisitMemberIds()));
    }

    @PostMapping("/complete/family/{activitySessionId}")
    public ResponseEntity<List<ActivitySessionDtoOut>> completeFamilyActivity(@AuthenticationPrincipal Account account, @PathVariable Integer activitySessionId, @RequestBody @Valid CompleteFamilyActivityDtoIn dto) {
        CompleteActivityDtoIn completion = new CompleteActivityDtoIn();
        completion.setRequestKey(dto.getRequestKey());
        return ResponseEntity.status(200).body(activitySessionService.completeActivity(account.getId(), activitySessionId, completion, dto.getVisitMemberIds()));
    }

}