package com.example.sikka.Repository;

import com.example.sikka.Model.PrivacyConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrivacyConsentRepository extends JpaRepository<PrivacyConsent, Integer> {

    PrivacyConsent findPrivacyConsentById(Integer id);

    List<PrivacyConsent> findPrivacyConsentsByVisitorProfileId(Integer visitorProfileId);

    PrivacyConsent findTopByVisitorProfileIdAndConsentKindOrderByCreatedAtDescIdDesc(Integer visitorProfileId, String consentKind);
}