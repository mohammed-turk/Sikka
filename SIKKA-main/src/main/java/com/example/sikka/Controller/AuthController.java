package com.example.sikka.Controller;

import com.example.sikka.Api.ApiResponse;
import com.example.sikka.DtoIn.EmailDtoIn;
import com.example.sikka.DtoIn.VerifyEmailDtoIn;
import com.example.sikka.Service.VerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.sikka.DtoIn.ResetPasswordDtoIn;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final VerificationService verificationService;

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody @Valid VerifyEmailDtoIn dto) {
        verificationService.verify(dto.getEmail(), dto.getCode());
        return ResponseEntity.status(200).body(new ApiResponse("email verified successfully"));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody @Valid EmailDtoIn dto) {
        verificationService.resendVerificationCode(dto.getEmail());
        return ResponseEntity.status(200).body(new ApiResponse("if the account exists and is not verified, a new code was sent"));
    }

    @PostMapping("/verify-phone")
    public ResponseEntity<?> verifyPhone(@RequestBody @Valid VerifyEmailDtoIn dto) {
        verificationService.verifyPhone(dto.getEmail(), dto.getCode());
        return ResponseEntity.status(200).body(new ApiResponse("phone verified successfully"));
    }

    @PostMapping("/resend-phone-verification")
    public ResponseEntity<?> resendPhoneVerification(@RequestBody @Valid EmailDtoIn dto) {
        verificationService.resendPhoneCode(dto.getEmail());
        return ResponseEntity.status(200).body(new ApiResponse("if the account has an unverified phone, a new code was sent"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody @Valid EmailDtoIn dto) {
        verificationService.sendResetCode(dto.getEmail());
        return ResponseEntity.status(200).body(new ApiResponse("if the account exists, a reset code was sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody @Valid ResetPasswordDtoIn dto) {
        verificationService.resetPassword(dto.getEmail(), dto.getCode(), dto.getNewPassword());
        return ResponseEntity.status(200).body(new ApiResponse("password reset successfully"));
    }
}