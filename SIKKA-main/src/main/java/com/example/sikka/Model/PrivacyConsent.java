package com.example.sikka.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PrivacyConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "Consent kind cannot be blank")
    @Pattern(regexp = "^(HEALTH_STORAGE|LOCATION_SHARING|WHATSAPP|EMAIL_MARKETING)$",
            message = "Consent kind must be HEALTH_STORAGE, LOCATION_SHARING, WHATSAPP or EMAIL_MARKETING")
    @Column(nullable = false, length = 30)
    private String consentKind;

    @NotNull(message = "Granted status cannot be null")
    @Column(nullable = false)
    private Boolean granted;

    @NotBlank(message = "Policy version cannot be blank")
    @Size(max = 30, message = "Policy version must not exceed 30 characters")
    @Column(nullable = false, length = 30)
    private String policyVersion;

    private LocalDateTime grantedAt;

    private LocalDateTime revokedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "visitor_profile_id", nullable = false)
    @JsonIgnore
    private VisitorProfile visitorProfile;

    @ManyToOne
    @JoinColumn(name = "granted_by_account_id", nullable = false)
    @JsonIgnore
    private Account grantedByAccount;

    @PrePersist
    public void setCreatedAt() {
        createdAt = LocalDateTime.now();
    }
}