package com.example.sikka.Controller;
import com.example.sikka.Model.Account;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.example.sikka.Api.ApiResponse;
import com.example.sikka.DtoIn.VisitGroupDtoIn;
import com.example.sikka.Service.VisitGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;


@RestController
@RequestMapping("/api/v1/visit-group")
@RequiredArgsConstructor
public class VisitGroupController {

    private final VisitGroupService visitGroupService;

    @GetMapping("/get")
    public ResponseEntity<?> get() {
        return ResponseEntity.status(200).body(visitGroupService.get());
    }

    @PostMapping("/add")
    public ResponseEntity<?> add(@RequestBody @Valid VisitGroupDtoIn dto) {
        String code = visitGroupService.add(dto);
        return ResponseEntity.status(200).body(new ApiResponse("added successfully, invite code: " + code));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody @Valid VisitGroupDtoIn dto) {
        visitGroupService.update(id, dto);
        return ResponseEntity.status(200).body(new ApiResponse("updated successfully"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        visitGroupService.delete(id);
        return ResponseEntity.status(200).body(new ApiResponse("deleted successfully"));
    }

    @PostMapping("/add-group/{visitId}")
    public ResponseEntity<?> addGroupToVisit(@AuthenticationPrincipal Account account, @PathVariable Integer visitId,
                                             @RequestParam String groupName) {
        String code = visitGroupService.addGroupToVisit(account.getId(), visitId, groupName);
        return ResponseEntity.status(200).body(new ApiResponse("group added, invite code: " + code));
    }

    @PutMapping("/regenerate-code/{visitId}")
    public ResponseEntity<?> regenerateCode(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        String code = visitGroupService.regenerateCode(account.getId(), visitId);
        return ResponseEntity.status(200).body(new ApiResponse("new invite code: " + code));
    }

    @PutMapping("/toggle-invitations/{visitId}")
    public ResponseEntity<?> toggleInvitations(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        boolean enabled = visitGroupService.toggleInvitations(account.getId(), visitId);
        return ResponseEntity.status(200).body(new ApiResponse(enabled ? "invitations enabled" : "invitations disabled"));
    }

    @GetMapping("/qr/{visitId}")
    public ResponseEntity<byte[]> qr(@AuthenticationPrincipal Account account, @PathVariable Integer visitId,
                                     @RequestParam String code) {
        byte[] png = visitGroupService.qrCode(account.getId(), visitId, code);
        return ResponseEntity.status(200).contentType(MediaType.IMAGE_PNG).body(png);
    }

    @GetMapping("/link/{visitId}")
    public ResponseEntity<?> link(@AuthenticationPrincipal Account account, @PathVariable Integer visitId,
                                  @RequestParam String code) {
        String link = visitGroupService.joinLink(account.getId(), visitId, code);
        return ResponseEntity.status(200).body(new ApiResponse(link));
    }

}