package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.PrivacyConsent;
import com.example.sikka.Model.VisitorProfile;
import com.example.sikka.Repository.AccountRepository;
import com.example.sikka.Repository.PrivacyConsentRepository;
import com.example.sikka.Repository.VisitorProfileRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class PrivacyConsentService {

    private final PrivacyConsentRepository privacyConsentRepository;
    private final VisitorProfileRepository visitorProfileRepository;
    private final AccountRepository accountRepository;

    public List<PrivacyConsent> getMyPrivacyConsents(Integer accountId){
        Account account = accountRepository.findAccountById(accountId);

        if (account == null){
            throw new ApiException("Account not found");
        }

        VisitorProfile visitorProfile = visitorProfileRepository.findVisitorProfileByAccountId(accountId);

        if (visitorProfile == null){
            throw new ApiException("Visitor profile not found");
        }

        return privacyConsentRepository.findPrivacyConsentsByVisitorProfileId(visitorProfile.getId());
    }

    public PrivacyConsent getPrivacyConsentById(Integer accountId, Integer consentId){
        PrivacyConsent consent = privacyConsentRepository.findPrivacyConsentById(consentId);

        if (consent == null){
            throw new ApiException("Privacy consent not found");
        }

        if (!consent.getVisitorProfile().getAccount().getId().equals(accountId)){
            throw new ApiException("You cannot access another user's consent");
        }

        return consent;
    }

    @Transactional
    public void addPrivacyConsent(Integer accountId, PrivacyConsent privacyConsent){
        if (privacyConsent == null || privacyConsent.getConsentKind() == null || !privacyConsent.getConsentKind().matches("^(HEALTH_STORAGE|LOCATION_SHARING|WHATSAPP|EMAIL_MARKETING)$")) {
            throw new ApiException("Invalid consent kind");
        }
        if (privacyConsent.getPolicyVersion() == null || privacyConsent.getPolicyVersion().isBlank() || privacyConsent.getPolicyVersion().length() > 30) {
            throw new ApiException("Policy version is required and must not exceed 30 characters");
        }
        Account account = accountRepository.findAccountById(accountId);

        if (account == null){
            throw new ApiException("Account not found");
        }

        VisitorProfile visitorProfile = visitorProfileRepository.findVisitorProfileByAccountId(accountId);

        if (visitorProfile == null){
            throw new ApiException("Visitor profile not found");
        }

        if (!Boolean.TRUE.equals(privacyConsent.getGranted())){
            throw new ApiException("Consent must be granted when adding a new consent");
        }

        PrivacyConsent latestConsent = privacyConsentRepository.findTopByVisitorProfileIdAndConsentKindOrderByCreatedAtDescIdDesc(
                visitorProfile.getId(), privacyConsent.getConsentKind());

        if (latestConsent != null && Boolean.TRUE.equals(latestConsent.getGranted())){
            if (latestConsent.getPolicyVersion().equals(privacyConsent.getPolicyVersion())){
                throw new ApiException("Consent is already granted for this policy version");
            }
        }

        PrivacyConsent newConsent = new PrivacyConsent();
        newConsent.setConsentKind(privacyConsent.getConsentKind());
        newConsent.setGranted(true);
        newConsent.setPolicyVersion(privacyConsent.getPolicyVersion());
        newConsent.setGrantedAt(LocalDateTime.now());
        newConsent.setRevokedAt(null);
        newConsent.setVisitorProfile(visitorProfile);
        newConsent.setGrantedByAccount(account);

        privacyConsentRepository.save(newConsent);
    }

    @Transactional
    public void revokePrivacyConsent(Integer accountId, Integer consentId){
        PrivacyConsent consent = privacyConsentRepository.findPrivacyConsentById(consentId);

        if (consent == null){
            throw new ApiException("Privacy consent not found");
        }

        if (!consent.getVisitorProfile().getAccount().getId().equals(accountId)){
            throw new ApiException("You cannot revoke another user's consent");
        }

        PrivacyConsent latestConsent = privacyConsentRepository.findTopByVisitorProfileIdAndConsentKindOrderByCreatedAtDescIdDesc(
                consent.getVisitorProfile().getId(), consent.getConsentKind());

        if (latestConsent == null || !latestConsent.getId().equals(consent.getId())){
            throw new ApiException("Only the latest consent can be revoked");
        }

        if (!Boolean.TRUE.equals(consent.getGranted())){
            throw new ApiException("Consent is already revoked");
        }

        consent.setGranted(false);
        consent.setRevokedAt(LocalDateTime.now());

        privacyConsentRepository.save(consent);
    }

    public boolean hasConsent(Integer profileId, String kind){
        VisitorProfile visitorProfile = visitorProfileRepository.findVisitorProfileById(profileId);

        if (visitorProfile == null){
            throw new ApiException("Visitor profile not found");
        }

        if (kind == null || (!"HEALTH_STORAGE".equals(kind) && !"LOCATION_SHARING".equals(kind) && !"WHATSAPP".equals(kind) && !"EMAIL_MARKETING".equals(kind))){
            throw new ApiException("Invalid consent kind");
        }

        PrivacyConsent consent = privacyConsentRepository.findTopByVisitorProfileIdAndConsentKindOrderByCreatedAtDescIdDesc(profileId, kind);

        return consent != null && Boolean.TRUE.equals(consent.getGranted()) && consent.getRevokedAt() == null;
    }
}