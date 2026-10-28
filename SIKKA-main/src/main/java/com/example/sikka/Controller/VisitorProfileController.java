package com.example.sikka.Controller;

import com.example.sikka.Api.ApiResponse;
import com.example.sikka.DtoIn.VisitorProfileDtoIn;
import com.example.sikka.Service.VisitorProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.sikka.DtoIn.UpdateProfileDtoIn;
import com.example.sikka.Model.Account;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
@RestController
@RequestMapping("/api/v1/visitor-profile")
@RequiredArgsConstructor
public class VisitorProfileController {

    private final VisitorProfileService visitorProfileService;

    @GetMapping("/get")
    public ResponseEntity<?> get() {
        return ResponseEntity.status(200).body(visitorProfileService.get());
    }

    @PostMapping("/add")
    public ResponseEntity<?> add(@RequestBody @Valid VisitorProfileDtoIn dto) {
        visitorProfileService.add(dto);
        return ResponseEntity.status(200).body(new ApiResponse("added successfully"));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody @Valid VisitorProfileDtoIn dto) {
        visitorProfileService.update(id, dto);
        return ResponseEntity.status(200).body(new ApiResponse("updated successfully"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        visitorProfileService.delete(id);
        return ResponseEntity.status(200).body(new ApiResponse("deleted successfully"));
    }

    @GetMapping("/my-profile")
    public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal Account account) {
        return ResponseEntity.status(200).body(visitorProfileService.getByAccount(account.getId()));
    }

    @PutMapping("/update-my-profile")
    public ResponseEntity<?> updateMyProfile(@AuthenticationPrincipal Account account, @RequestBody @Valid UpdateProfileDtoIn dto) {
        visitorProfileService.updateByAccount(account.getId(), dto);
        return ResponseEntity.status(200).body(new ApiResponse("profile updated successfully"));
    }
}