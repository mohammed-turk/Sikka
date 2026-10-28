package com.example.sikka.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_visit_plan_visit_owner_history", columnNames = {"visit_id", "owner_visit_member_id", "generated_by_history_id"}))
public class VisitPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "Plan kind is required")
    @Pattern(regexp = "^(INDIVIDUAL|GROUP)$", message = "Plan kind must be INDIVIDUAL or GROUP")
    @Column(columnDefinition = "VARCHAR(20) not null")
    private String planKind;

    @NotNull(message = "Timing mode is required")
    @Pattern(regexp = "^(RELATIVE|SCHEDULED)$", message = "Timing mode must be RELATIVE or SCHEDULED")
    @Column(columnDefinition = "VARCHAR(20) not null default 'RELATIVE'")
    private String timingMode = "RELATIVE";

    @NotNull(message = "Active status is required")
    @Column(name = "is_active", columnDefinition = "BOOLEAN not null")
    private Boolean isActive = false;

    @Column(columnDefinition = "JSON")
    private String proposedItems;

    @Column(columnDefinition = "JSON")
    private String excludedRides;

    @Pattern(regexp = "^(INDIVIDUAL|GROUP)$", message = "Pending scope must be INDIVIDUAL or GROUP")
    @Column(columnDefinition = "VARCHAR(20)")
    private String pendingScope;

    @Column(columnDefinition = "JSON")
    private String affectedMemberIds;

    @jakarta.validation.constraints.Size(max = 500, message = "Proposal reason must not exceed 500 characters")
    @Column(length = 500)
    private String proposalReason;

    private LocalDateTime proposedAt;

    @Column(columnDefinition = "JSON")
    private String proposalBaseSnapshot;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "visit_id", nullable = false)
    @JsonIgnore
    private Visit visit;

    @ManyToOne
    @JoinColumn(name = "owner_visit_member_id")
    @JsonIgnore
    private VisitMember ownerVisitMember;

    @ManyToOne
    @JoinColumn(name = "generated_by_history_id")
    @JsonIgnore
    private PlanHistory generatedByHistory;

    @ManyToOne
    @JoinColumn(name = "requested_by_visit_member_id")
    @JsonIgnore
    private VisitMember requestedByVisitMember;

    @OneToMany(mappedBy = "visitPlan")
    @JsonIgnore
    private Set<PlanItem> planItems;

    @OneToOne(mappedBy = "visitPlan")
    @JsonIgnore
    private PlanApproval planApproval;
}