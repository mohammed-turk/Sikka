package com.example.sikka.Controller;

import com.example.sikka.Api.ApiResponse;
import com.example.sikka.DtoIn.ReuseVisitDtoIn;
import com.example.sikka.DtoIn.VisitDtoIn;
import com.example.sikka.DtoIn.UpdateVisitArrivalDtoIn;
import com.example.sikka.DtoOut.PreviewDtoOut;
import com.example.sikka.DtoOut.ReuseVisitDtoOut;
import com.example.sikka.Service.VisitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.sikka.DtoIn.CreateVisitDtoIn;
import com.example.sikka.DtoIn.ReuseVisitDtoIn;
import com.example.sikka.Model.Account;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/v1/visit")
@RequiredArgsConstructor
public class VisitController {

    private final VisitService visitService;

    @GetMapping("/get")
    public ResponseEntity<?> get() {
        return ResponseEntity.status(200).body(visitService.get());
    }

    @PostMapping("/add")
    public ResponseEntity<?> add(@RequestBody @Valid VisitDtoIn dto) {
        visitService.add(dto);
        return ResponseEntity.status(200).body(new ApiResponse("added successfully"));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody @Valid VisitDtoIn dto) {
        visitService.update(id, dto);
        return ResponseEntity.status(200).body(new ApiResponse("updated successfully"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> delete(@AuthenticationPrincipal Account account, @PathVariable Integer id) {
        visitService.delete(account.getId(), id);
        return ResponseEntity.status(200).body(new ApiResponse("Visit and its related data deleted successfully"));
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(@AuthenticationPrincipal Account account, @RequestBody @Valid CreateVisitDtoIn dto) {
        String code = visitService.create(account.getId(), dto);
        if (code == null)
            return ResponseEntity.status(200).body(new ApiResponse("visit created"));
        return ResponseEntity.status(200).body(new ApiResponse("visit created, invite code: " + code));
    }

    @GetMapping("/my-visits")
    public ResponseEntity<?> myVisits(@AuthenticationPrincipal Account account) {
        return ResponseEntity.status(200).body(visitService.myVisits(account.getId()));
    }

    @GetMapping("/details/{visitId}")
    public ResponseEntity<?> details(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        return ResponseEntity.status(200).body(visitService.getVisitDetails(account.getId(), visitId));
    }

    @PutMapping("/update-visit/{visitId}")
    public ResponseEntity<?> updateVisit(@AuthenticationPrincipal Account account, @PathVariable Integer visitId,
                                         @RequestBody @Valid CreateVisitDtoIn dto) {
        visitService.updateByOrganizer(account.getId(), visitId, dto);
        return ResponseEntity.status(200).body(new ApiResponse("visit updated successfully"));
    }

    @PutMapping("/cancel/{visitId}")
    public ResponseEntity<?> cancel(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        visitService.cancel(account.getId(), visitId);
        return ResponseEntity.status(200).body(new ApiResponse("visit cancelled"));
    }

    @PutMapping("/start/{visitId}")
    public ResponseEntity<?> start(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        visitService.startVisit(account.getId(), visitId);
        return ResponseEntity.status(200).body(new ApiResponse("visit started"));
    }

    @PutMapping("/complete/{visitId}")
    public ResponseEntity<?> complete(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        visitService.completeVisit(account.getId(), visitId);
        return ResponseEntity.status(200).body(new ApiResponse("visit completed"));
    }

    @GetMapping("/preview/{visitId}")
    public ResponseEntity<PreviewDtoOut> getVisitPreview(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        return ResponseEntity.status(200).body(visitService.getVisitPreview(account.getId(), visitId));
    }

    @PutMapping("/arrival/{visitId}")
    public ResponseEntity<ApiResponse> updateVisitArrival(@AuthenticationPrincipal Account account, @PathVariable Integer visitId, @RequestBody @Valid UpdateVisitArrivalDtoIn updateVisitArrivalDtoIn) {
        visitService.updateVisitArrival(account.getId(), visitId, updateVisitArrivalDtoIn);
        return ResponseEntity.status(200).body(new ApiResponse("Arrival time updated and activity times scheduled successfully"));
    }

    @PostMapping("/reuse/{sourceVisitId}")
    public ResponseEntity<ReuseVisitDtoOut> reuseVisit(@AuthenticationPrincipal Account account, @PathVariable Integer sourceVisitId, @RequestBody @Valid ReuseVisitDtoIn reuseVisitDtoIn) {
        return ResponseEntity.status(201).body(visitService.reuseVisit(account.getId(), sourceVisitId, reuseVisitDtoIn));
    }




}