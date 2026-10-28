package com.example.sikka.Controller;

import com.example.sikka.Api.ApiResponse;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.PrivacyConsent;
import com.example.sikka.Service.PrivacyConsentService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/privacy-consent")
@AllArgsConstructor
public class PrivacyConsentController {

    private final PrivacyConsentService privacyConsentService;

    @GetMapping("/get/my/consents")
    public ResponseEntity<?> getMyPrivacyConsents(@AuthenticationPrincipal Account account){
        return ResponseEntity.status(200).body(privacyConsentService.getMyPrivacyConsents(account.getId()));
    }

    @GetMapping("/get/details/{consentId}")
    public ResponseEntity<?> getPrivacyConsentById(@AuthenticationPrincipal Account account, @PathVariable Integer consentId){
        return ResponseEntity.status(200).body(privacyConsentService.getPrivacyConsentById(account.getId(), consentId));
    }

    @PostMapping("/add")
    public ResponseEntity<?> addPrivacyConsent(@AuthenticationPrincipal Account account, @RequestBody @Valid PrivacyConsent privacyConsent){
        privacyConsentService.addPrivacyConsent(account.getId(), privacyConsent);
        return ResponseEntity.status(200).body(new ApiResponse("Privacy consent added successfully"));
    }

    @PutMapping("/revoke/{consentId}")
    public ResponseEntity<?> revokePrivacyConsent(@AuthenticationPrincipal Account account, @PathVariable Integer consentId){
        privacyConsentService.revokePrivacyConsent(account.getId(), consentId);
        return ResponseEntity.status(200).body(new ApiResponse("Privacy consent revoked successfully"));
    }
}