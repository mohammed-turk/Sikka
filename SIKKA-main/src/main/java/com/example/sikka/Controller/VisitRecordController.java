package com.example.sikka.Controller;

import com.example.sikka.Api.ApiResponse;
import com.example.sikka.DtoIn.CreateVisitRecordDtoIn;
import com.example.sikka.DtoIn.RateVisitRecordDtoIn;
import com.example.sikka.DtoIn.UpdateVisitRecordDtoIn;
import com.example.sikka.Service.VisitRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.example.sikka.Model.Account;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/visit-record")
public class VisitRecordController {

    private final VisitRecordService visitRecordService;

    @GetMapping("/get/my-records/{visitId}")
    public ResponseEntity<?> getMyVisitRecords(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        return ResponseEntity.status(200).body(visitRecordService.getMyVisitRecords(account.getId(), visitId));
    }

    @PostMapping("/complete/{visitId}")
    public ResponseEntity<?> completeVisitRecords(@AuthenticationPrincipal Account account, @PathVariable Integer visitId, @RequestBody @Valid CreateVisitRecordDtoIn dto) {
        visitRecordService.createVisitRecords(account.getId(), visitId, dto, 1);
        return ResponseEntity.status(201).body(new ApiResponse("Visit records created successfully"));
    }

    @PostMapping("/skip/{visitId}")
    public ResponseEntity<?> skipVisitRecords(@AuthenticationPrincipal Account account, @PathVariable Integer visitId, @RequestBody @Valid CreateVisitRecordDtoIn dto) {
        visitRecordService.createVisitRecords(account.getId(), visitId, dto, 2);
        return ResponseEntity.status(201).body(new ApiResponse("Visit records created successfully"));
    }

    @PutMapping("/update/complete")
    public ResponseEntity<?> correctVisitRecordToCompleted(@AuthenticationPrincipal Account account, @RequestBody @Valid UpdateVisitRecordDtoIn dto) {
        visitRecordService.updateVisitRecordStatus(account.getId(), dto, 1);
        return ResponseEntity.status(200).body(new ApiResponse("Visit record status updated successfully"));
    }

    @PutMapping("/update/skip")
    public ResponseEntity<?> correctVisitRecordToSkipped(@AuthenticationPrincipal Account account, @RequestBody @Valid UpdateVisitRecordDtoIn dto) {
        visitRecordService.updateVisitRecordStatus(account.getId(), dto, 2);
        return ResponseEntity.status(200).body(new ApiResponse("Visit record status updated successfully"));
    }

    @PutMapping("/rate")
    public ResponseEntity<?> rateVisitRecord(@AuthenticationPrincipal Account account, @RequestBody @Valid RateVisitRecordDtoIn rateVisitRecordDtoIn) {
        visitRecordService.rateVisitRecord(account.getId(), rateVisitRecordDtoIn);
        return ResponseEntity.status(200).body(new ApiResponse("Ride rating saved successfully"));
    }

    @GetMapping("/visit-summary/{visitId}")
    public ResponseEntity<?> getMyVisitSummary(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        return ResponseEntity.status(200).body(visitRecordService.getMyVisitSummary(account.getId(), visitId));
    }

    @GetMapping("/progress/{visitId}")
    public ResponseEntity<?> getMyVisitProgress(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        return ResponseEntity.status(200).body(visitRecordService.getMyVisitProgress(account.getId(), visitId));
    }
}
