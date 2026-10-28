package com.example.sikka.Controller;

import com.example.sikka.Api.ApiResponse;
import com.example.sikka.DtoIn.JoinDtoIn;
import com.example.sikka.DtoIn.ReviewDtoIn;
import com.example.sikka.DtoIn.SelectedRidesDtoIn;
import com.example.sikka.DtoIn.VisitMemberDtoIn;
import com.example.sikka.Service.VisitMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.sikka.Model.Account;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/v1/visit-member")
@RequiredArgsConstructor
public class VisitMemberController {

    private final VisitMemberService visitMemberService;

    @PostMapping("/remind/{memberId}")
    public ResponseEntity<?> remindVisitMember(@AuthenticationPrincipal Account account, @PathVariable Integer memberId) {
        visitMemberService.remindVisitMember(account.getId(), memberId);
        return ResponseEntity.status(200).body(new ApiResponse("Reminder sent"));
    }

    // ---------- CRUD ----------

    @GetMapping("/get")
    public ResponseEntity<?> get() {
        return ResponseEntity.status(200).body(visitMemberService.get());
    }

    @PostMapping("/add")
    public ResponseEntity<?> add(@RequestBody @Valid VisitMemberDtoIn dto) {
        visitMemberService.add(dto);
        return ResponseEntity.status(200).body(new ApiResponse("added successfully"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        visitMemberService.delete(id);
        return ResponseEntity.status(200).body(new ApiResponse("deleted successfully"));
    }

    // ---------- Flow 1 extras ----------

    @PostMapping("/join")
    public ResponseEntity<?> join(@AuthenticationPrincipal Account account, @RequestBody @Valid JoinDtoIn dto) {
        visitMemberService.join(account.getId(), dto);
        return ResponseEntity.status(200).body(new ApiResponse("joined successfully"));
    }

    @PutMapping("/rsvp/going/{visitId}")
    public ResponseEntity<?> confirmAttendance(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        visitMemberService.updateRsvp(account.getId(), visitId, 1);
        return ResponseEntity.status(200).body(new ApiResponse("rsvp updated successfully"));
    }

    @PutMapping("/rsvp/maybe/{visitId}")
    public ResponseEntity<?> markAttendanceAsMaybe(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        visitMemberService.updateRsvp(account.getId(), visitId, 2);
        return ResponseEntity.status(200).body(new ApiResponse("rsvp updated successfully"));
    }

    @PutMapping("/rsvp/not-going/{visitId}")
    public ResponseEntity<?> declineAttendance(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        visitMemberService.updateRsvp(account.getId(), visitId, 3);
        return ResponseEntity.status(200).body(new ApiResponse("rsvp updated successfully"));
    }

    @PutMapping("/update-selected-rides/{visitId}")
    public ResponseEntity<?> updateSelectedRides(@AuthenticationPrincipal Account account, @PathVariable Integer visitId, @RequestBody @Valid SelectedRidesDtoIn dto) {
        visitMemberService.updateSelectedRides(account.getId(), visitId, dto);
        return ResponseEntity.status(200).body(new ApiResponse("selected rides updated successfully"));
    }

    @PutMapping("/check-in/{visitId}")
    public ResponseEntity<?> checkIn(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        visitMemberService.checkIn(account.getId(), visitId);
        return ResponseEntity.status(200).body(new ApiResponse("checked in successfully"));
    }

    @PutMapping("/review/{visitId}")
    public ResponseEntity<?> review(@AuthenticationPrincipal Account account, @PathVariable Integer visitId, @RequestBody @Valid ReviewDtoIn dto) {
        visitMemberService.review(account.getId(), visitId, dto);
        return ResponseEntity.status(200).body(new ApiResponse("review saved successfully"));
    }

    @DeleteMapping("/leave/{visitId}")
    public ResponseEntity<?> leave(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        visitMemberService.leave(account.getId(), visitId);
        return ResponseEntity.status(200).body(new ApiResponse("left the visit successfully"));
    }

    @DeleteMapping("/remove/{visitId}/{memberId}")
    public ResponseEntity<?> remove(@AuthenticationPrincipal Account account, @PathVariable Integer visitId, @PathVariable Integer memberId) {
        visitMemberService.remove(account.getId(), visitId, memberId);
        return ResponseEntity.status(200).body(new ApiResponse("member removed successfully"));
    }

    @GetMapping("/summary/{visitId}")
    public ResponseEntity<?> summary(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        return ResponseEntity.status(200).body(visitMemberService.getSummary(account.getId(), visitId));
    }

    @PostMapping("/add-family/{visitId}")
    public ResponseEntity<?> addFamilyMembers(@AuthenticationPrincipal Account account, @PathVariable Integer visitId, @RequestBody @Valid com.example.sikka.DtoIn.AddFamilyMembersDtoIn dto) {
        visitMemberService.addFamilyMembers(account.getId(), visitId, dto);
        return ResponseEntity.status(200).body(new ApiResponse("Family members added successfully"));
    }

    @PutMapping("/preferences/{visitMemberId}")
    public ResponseEntity<?> saveMemberPreferences(@AuthenticationPrincipal Account account, @PathVariable Integer visitMemberId, @RequestBody @Valid com.example.sikka.DtoIn.MemberPreferencesDtoIn dto) {
        visitMemberService.saveMemberPreferences(account.getId(), visitMemberId, dto, 1);
        return ResponseEntity.status(200).body(new ApiResponse("Planning answers saved successfully"));
    }

    @GetMapping("/preferences/{visitMemberId}")
    public ResponseEntity<?> getMemberPreferences(@AuthenticationPrincipal Account account, @PathVariable Integer visitMemberId) {
        return ResponseEntity.status(200).body(visitMemberService.getMemberPreferences(account.getId(), visitMemberId));
    }

    @PutMapping("/preferences/skip/{visitMemberId}")
    public ResponseEntity<?> skipMemberPreferences(@AuthenticationPrincipal Account account, @PathVariable Integer visitMemberId) {
        visitMemberService.saveMemberPreferences(account.getId(), visitMemberId, new com.example.sikka.DtoIn.MemberPreferencesDtoIn(), 2);
        return ResponseEntity.status(200).body(new ApiResponse("Planning answers skipped successfully"));
    }

}
