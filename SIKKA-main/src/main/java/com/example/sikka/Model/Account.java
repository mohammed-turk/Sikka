package com.example.sikka.Model;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.Collections;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Set;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Account implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(columnDefinition = "varchar(100) not null")
    private String name;

    @Column(columnDefinition = "varchar(150) not null unique")
    private String email;

    @Column(columnDefinition = "varchar(255) not null")
    private String passwordHash;

    @Pattern(regexp = "^(USER|ADMIN)$", message = "role must be USER or ADMIN")
    @Column(columnDefinition = "varchar(10) not null default 'USER'")
    private String role;

    @Pattern(regexp = "^(ACTIVE|SUSPENDED|BLOCKED)$", message = "status must be ACTIVE, SUSPENDED, or BLOCKED")
    @Column(columnDefinition = "varchar(10) not null default 'ACTIVE'")
    private String status;

    @Column(columnDefinition = "boolean not null default false")
    private Boolean emailVerified = false;

    @Column(columnDefinition = "varchar(255)")
    private String verificationTokenHash;

    @Column(columnDefinition = "datetime")
    private LocalDateTime verificationExpiresAt;

    @Column(columnDefinition = "boolean not null default false")
    private Boolean phoneVerified = false;

    @Column(columnDefinition = "varchar(255)")
    private String phoneCodeHash;

    @Column(columnDefinition = "datetime")
    private LocalDateTime phoneCodeExpiresAt;

    @Column(columnDefinition = "varchar(255)")
    private String resetTokenHash;

    @Column(columnDefinition = "datetime")
    private LocalDateTime resetExpiresAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToOne(cascade = CascadeType.ALL, mappedBy = "account")
    private VisitorProfile visitorProfile;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "organizer")
    private Set<VisitGroup> organizedGroups;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "organizer")
    private Set<Visit> organizedVisits;

    @OneToMany(mappedBy = "account")
    private Set<Notification> notifications;

    @OneToMany(mappedBy = "approvedByAccount")
    private Set<PlanApproval> planApprovals;

    @OneToMany(mappedBy = "recordedByAccount")
    private Set<VisitRecord> visitRecords;

    @Column(columnDefinition = "varchar(20)")
    private String phone;

    @OneToMany(mappedBy = "buyerAccount")
    private Set<PaymentOrder> paymentOrders;

    @OneToMany(mappedBy = "grantedByAccount")
    private Set<PrivacyConsent> privacyConsents;

    @OneToMany(mappedBy = "account")
    @JsonIgnore
    private Set<PlanHistory> planHistories;

    @JsonIgnore
    @Min(value = 0, message = "Archived free generations must not be negative")
    @Column(nullable = false, columnDefinition = "bigint not null default 0")
    private Long archivedFreeGenerations = 0L;

    @JsonIgnore
    @Min(value = 0, message = "Archived free visits must not be negative")
    @Column(nullable = false, columnDefinition = "bigint not null default 0")
    private Long archivedFreeVisits = 0L;

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singleton(new SimpleGrantedAuthority(this.role));
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return this.passwordHash;
    }

    @Override
    @JsonIgnore
    public String getUsername() {
        return this.email;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return !"BLOCKED".equals(this.status);
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return "ACTIVE".equals(this.status);
    }

}