package com.example.sikka.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class PlanApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "Approval status is required")
    @Pattern(regexp = "^(PENDING|APPROVED|REJECTED)$", message = "Approval status must be PENDING, APPROVED or REJECTED")
    @Column(columnDefinition = "VARCHAR(20) not null")
    private String status = "PENDING";

    @NotNull(message = "Approval scope is required")
    @Pattern(regexp = "^(INDIVIDUAL|GROUP)$", message = "Approval scope must be INDIVIDUAL or GROUP")
    @Column(columnDefinition = "VARCHAR(20) not null default 'GROUP'")
    private String approvalScope = "GROUP";

    private LocalDateTime respondedAt;

    @OneToOne
    @JoinColumn(name = "visit_plan_id", nullable = false, unique = true)
    @JsonIgnore
    private VisitPlan visitPlan;

    @ManyToOne
    @JoinColumn(name = "requested_by_account_id")
    @JsonIgnore
    private Account requestedByAccount;

    @ManyToOne
    @JoinColumn(name = "required_approver_account_id")
    @JsonIgnore
    private Account requiredApproverAccount;

    @ManyToOne
    @JoinColumn(name = "approved_by_account_id")
    @JsonIgnore
    private Account approvedByAccount;
}