package com.example.sikka.Controller;

import com.example.sikka.Api.ApiResponse;
import com.example.sikka.DtoIn.FamilyMemberDtoIn;
import com.example.sikka.Model.Account;
import com.example.sikka.Service.FamilyMemberService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/family-member")
@AllArgsConstructor
public class FamilyMemberController {

    private final FamilyMemberService familyMemberService;

    @GetMapping("/get/my")
    public ResponseEntity<?> getMyFamilyMembers(@AuthenticationPrincipal Account account){
        return ResponseEntity.status(200).body(
                familyMemberService.getMyFamilyMembers(account.getId())
        );
    }

    @GetMapping("/get/details/{familyMemberId}")
    public ResponseEntity<?> getFamilyMemberById(@AuthenticationPrincipal Account account, @PathVariable Integer familyMemberId){
        return ResponseEntity.status(200).body(
                familyMemberService.getFamilyMemberById(account.getId(), familyMemberId)
        );
    }

    @PostMapping("/add")
    public ResponseEntity<?> addFamilyMember(@AuthenticationPrincipal Account account, @RequestBody @Valid FamilyMemberDtoIn dto){
        familyMemberService.addFamilyMember(account.getId(), dto);
        return ResponseEntity.status(200).body(new ApiResponse("Family member added successfully"));
    }

    @PutMapping("/update/{familyMemberId}")
    public ResponseEntity<?> updateFamilyMember(@AuthenticationPrincipal Account account, @PathVariable Integer familyMemberId, @RequestBody @Valid FamilyMemberDtoIn dto){
        familyMemberService.updateFamilyMember(account.getId(), familyMemberId, dto);
        return ResponseEntity.status(200).body(new ApiResponse("Family member updated successfully"));
    }

    @DeleteMapping("/delete/{familyMemberId}")
    public ResponseEntity<?> deleteFamilyMember(@AuthenticationPrincipal Account account, @PathVariable Integer familyMemberId){
        familyMemberService.deleteFamilyMember(account.getId(), familyMemberId);
        return ResponseEntity.status(200).body(new ApiResponse("Family member deleted successfully"));
    }
}