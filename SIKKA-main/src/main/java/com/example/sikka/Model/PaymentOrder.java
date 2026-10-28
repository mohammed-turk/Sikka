package com.example.sikka.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "Order group key cannot be blank")
    @Size(max = 80, message = "Order group key must not exceed 80 characters")
    @Column(nullable = false, length = 80)
    private String orderGroupKey;

    @NotNull(message = "Attempt number cannot be null")
    @Min(value = 1, message = "Attempt number must be at least 1")
    @Column(nullable = false)
    private Integer attemptNo = 1;

    @NotBlank(message = "Payment kind cannot be blank")
    @Pattern(regexp = "^(INITIAL|RETRY)$", message = "Payment kind must be INITIAL or RETRY")
    @Column(nullable = false, length = 10)
    private String kind = "INITIAL";

    @NotNull(message = "Payment amount cannot be null")
    @Positive(message = "Payment amount must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Invalid payment amount")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amountSar;

    @NotBlank(message = "Payment status cannot be blank")
    @Pattern(regexp = "^(PENDING|PAID|FAILED)$", message = "Payment status must be PENDING, PAID or FAILED")
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Size(max = 150, message = "Moyasar payment ID must not exceed 150 characters")
    @Column(unique = true, length = 150)
    private String moyasarPaymentId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime verifiedAt;

    private LocalDateTime paidAt;

    @Size(max = 500, message = "Failure reason must not exceed 500 characters")
    @Column(length = 500)
    private String failureReason;

    @ManyToOne
    @JoinColumn(name = "visit_id", nullable = true)
    @JsonIgnore
    private Visit visit;

    @ManyToOne
    @JoinColumn(name = "buyer_account_id", nullable = false)
    @JsonIgnore
    private Account buyerAccount;

    @PrePersist
    public void setCreatedAt() {
        createdAt = LocalDateTime.now();
    }

    private LocalDateTime consumedAt;

    @OneToOne
    @JoinColumn(name = "consumed_by_plan_history_id", unique = true)
    @JsonIgnore
    private PlanHistory consumedByPlanHistory;

    @Transient
    @com.fasterxml.jackson.annotation.JsonProperty("visitId")
    public Integer getPaymentVisitId() {
        return visit == null ? null : visit.getId();
    }

}