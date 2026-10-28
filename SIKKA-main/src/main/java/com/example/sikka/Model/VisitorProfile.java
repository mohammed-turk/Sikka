package com.example.sikka.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class VisitorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(columnDefinition = "varchar(100) not null")
    private String name;

    @Column(columnDefinition = "date")
    private LocalDate birthDate;

    @Column(columnDefinition = "decimal(5,2)")
    private BigDecimal heightCm;

    @Column(columnDefinition = "decimal(6,2)")
    private BigDecimal weightKg;

    @Column(columnDefinition = "varchar(10)")
    private String gender;

    @Column(columnDefinition = "varchar(30)")
    @Pattern(regexp = "^(LOW|MILD|EXTREME)$", message = "Invalid thrill preference")
    private String thrillPreference;

    @Column(columnDefinition = "json")
    private String preferredRideTypes;

    @Column(columnDefinition = "json")
    private String dislikedRideFeatures;

    @Column(columnDefinition = "json")
    private String foodPreferences;

    @Column(columnDefinition = "json")
    private String healthData;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToOne
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    @JsonIgnore
    private Account account;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "visitorProfile")
    @JsonIgnore
    private Set<VisitMember> visitMembers;

    @OneToMany(mappedBy = "visitorProfile")
    @JsonIgnore
    private Set<RidePreference> ridePreferences;

    @OneToMany(mappedBy = "visitorProfile")
    @JsonIgnore
    private Set<PrivacyConsent> privacyConsents;
}