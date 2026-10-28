package com.example.sikka.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_plan_history_account_request", columnNames = {"account_id", "request_key"}))
public class PlanHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "Request key is required")
    @Size(max = 80, message = "Request key must not exceed 80 characters")
    @Column(name = "request_key", nullable = false, length = 80, updatable = false)
    private String requestKey;

    @NotBlank(message = "Operation type is required")
    @Pattern(regexp = "^(INITIAL|REGENERATE|REUSE)$", message = "Operation type must be INITIAL, REGENERATE or REUSE")
    @Column(nullable = false, length = 20)
    private String operationType;

    @NotBlank(message = "Generation status is required")
    @Pattern(regexp = "^(PROCESSING|SUCCEEDED|FAILED)$", message = "Generation status must be PROCESSING, SUCCEEDED or FAILED")
    @Column(nullable = false, length = 20)
    private String status = "PROCESSING";

    @NotBlank(message = "Billing mode is required")
    @Pattern(regexp = "^(FREE|PAID)$", message = "Billing mode must be FREE or PAID")
    @Column(nullable = false, length = 10)
    private String billingMode;

    @NotNull(message = "Quota counting status is required")
    @Column(nullable = false)
    private Boolean countsTowardQuota = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @Size(max = 500, message = "Failure reason must not exceed 500 characters")
    @Column(length = 500)
    private String failureReason;

    @Column(columnDefinition = "JSON")
    private String planningInputSnapshot;

    @Column(columnDefinition = "JSON")
    private String generationResultSnapshot;

    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    @JsonIgnore
    private Account account;

    @ManyToOne
    @JoinColumn(name = "visit_id", nullable = false, updatable = false)
    @JsonIgnore
    private Visit visit;

    @ManyToOne
    @JoinColumn(name = "payment_order_id")
    @JsonIgnore
    private PaymentOrder paymentOrder;

    @ManyToOne
    @JoinColumn(name = "source_visit_id", updatable = false)
    @JsonIgnore
    private Visit sourceVisit;
}