package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.Model.Account;
import com.example.sikka.Repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final int CODE_MINUTES = 10;
    private final PasswordEncoder passwordEncoder;
    private final AccountRepository accountRepository;
    private final EmailService emailService;
    private final WhatsAppService whatsAppService;
    private final SecureRandom random = new SecureRandom();

    public void sendVerificationCode(Account account) {
        sendEmailCode(account);
        if (account.getPhone() != null && !account.getPhone().isBlank())
            sendPhoneCode(account);
    }

    private void sendEmailCode(Account account) {
        String code = newCode();
        account.setVerificationTokenHash(hash(code));
        account.setVerificationExpiresAt(LocalDateTime.now().plusMinutes(CODE_MINUTES));
        accountRepository.save(account);
        emailService.sendEmail(account.getEmail(), "Sikka verification code", "Your Sikka email verification code is " + code + ". It expires in " + CODE_MINUTES + " minutes.");
    }

    private void sendPhoneCode(Account account) {
        String code = newCode();
        account.setPhoneCodeHash(hash(code));
        account.setPhoneCodeExpiresAt(LocalDateTime.now().plusMinutes(CODE_MINUTES));
        accountRepository.save(account);
        whatsAppService.sendMessage(account.getPhone(), "Your Sikka phone verification code is " + code + ". It expires in " + CODE_MINUTES + " minutes.");
    }

    public void resendVerificationCode(String email) {
        Account account = accountRepository.findAccountByEmail(email);
        if (account == null || Boolean.TRUE.equals(account.getEmailVerified()))
            return;

        if (account.getVerificationExpiresAt() != null && account.getVerificationExpiresAt().isAfter(LocalDateTime.now().plusMinutes(CODE_MINUTES - 1)))
            throw new ApiException("please wait a minute before requesting another code");

        sendEmailCode(account);
    }

    public void verify(String email, String code) {
        Account account = accountRepository.findAccountByEmail(email);
        if (account == null || account.getVerificationTokenHash() == null || account.getVerificationExpiresAt() == null || account.getVerificationExpiresAt().isBefore(LocalDateTime.now()) || !MessageDigest.isEqual(hash(code).getBytes(StandardCharsets.UTF_8), account.getVerificationTokenHash().getBytes(StandardCharsets.UTF_8)))
            throw new ApiException("invalid or expired code");

        account.setEmailVerified(true);
        account.setVerificationTokenHash(null);
        account.setVerificationExpiresAt(null);
        accountRepository.save(account);
        emailService.sendEmail(account.getEmail(), "Sikka email verified", "Your email has been verified successfully. Welcome to Sikka!");
    }

    public void resendPhoneCode(String email) {
        Account account = accountRepository.findAccountByEmail(email);
        if (account == null || account.getPhone() == null || account.getPhone().isBlank() || Boolean.TRUE.equals(account.getPhoneVerified()))
            return;

        if (account.getPhoneCodeExpiresAt() != null && account.getPhoneCodeExpiresAt().isAfter(LocalDateTime.now().plusMinutes(CODE_MINUTES - 1)))
            throw new ApiException("please wait a minute before requesting another code");

        sendPhoneCode(account);
    }

    public void verifyPhone(String email, String code) {
        Account account = accountRepository.findAccountByEmail(email);
        if (account == null || account.getPhoneCodeHash() == null || account.getPhoneCodeExpiresAt() == null || account.getPhoneCodeExpiresAt().isBefore(LocalDateTime.now()) || !MessageDigest.isEqual(hash(code).getBytes(StandardCharsets.UTF_8), account.getPhoneCodeHash().getBytes(StandardCharsets.UTF_8)))
            throw new ApiException("invalid or expired code");

        account.setPhoneVerified(true);
        account.setPhoneCodeHash(null);
        account.setPhoneCodeExpiresAt(null);
        accountRepository.save(account);
        whatsAppService.sendMessage(account.getPhone(), "Your phone number has been verified successfully. Welcome to Sikka!");
    }

    // forgot password: unknown email is ignored silently (we do not reveal which emails exist)
    public void sendResetCode(String email) {
        Account account = accountRepository.findAccountByEmail(email);
        if (account == null)
            return;

        if (account.getResetExpiresAt() != null && account.getResetExpiresAt().isAfter(LocalDateTime.now().plusMinutes(CODE_MINUTES - 1)))
            throw new ApiException("please wait a minute before requesting another code");

        String code = newCode();
        account.setResetTokenHash(hash(code));
        account.setResetExpiresAt(LocalDateTime.now().plusMinutes(CODE_MINUTES));
        accountRepository.save(account);

        deliver(account, "Sikka password reset code", "Your Sikka password reset code is " + code + ". It expires in " + CODE_MINUTES + " minutes.");
    }

    public void resetPassword(String email, String code, String newPassword) {
        Account account = accountRepository.findAccountByEmail(email);
        if (account == null || account.getResetTokenHash() == null || account.getResetExpiresAt() == null || account.getResetExpiresAt().isBefore(LocalDateTime.now()) || !MessageDigest.isEqual(hash(code).getBytes(StandardCharsets.UTF_8), account.getResetTokenHash().getBytes(StandardCharsets.UTF_8)))
            throw new ApiException("invalid or expired code");

        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setResetTokenHash(null);
        account.setResetExpiresAt(null);
        accountRepository.save(account);
    }

    private void deliver(Account account, String subject, String text) {
        boolean sent = false;
        if (Boolean.TRUE.equals(account.getPhoneVerified()) && account.getPhone() != null && !account.getPhone().isBlank())
            sent = whatsAppService.sendMessage(account.getPhone(), text);
        if (!sent)
            emailService.sendEmail(account.getEmail(), subject, text);
    }

    private String newCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private String hash(String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(code.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new ApiException("hashing error");
        }
    }
}