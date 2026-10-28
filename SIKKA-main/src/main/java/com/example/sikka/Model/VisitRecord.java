package com.example.sikka.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(uniqueConstraints = {@UniqueConstraint(name = "uk_visit_record_account_member_request", columnNames = {"recorded_by_account_id", "visit_member_id", "request_key"}), @UniqueConstraint(name = "uk_visit_record_member_plan_item", columnNames = {"visit_member_id", "plan_item_id"})})
public class VisitRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Size(max = 80, message = "Request key must not exceed 80 characters")
    @Column(name = "request_key", length = 80, updatable = false)
    private String requestKey;

    @NotNull(message = "Record status is required")
    @Pattern(regexp = "^(COMPLETED|SKIPPED)$", message = "Record status must be COMPLETED or SKIPPED")
    @Column(columnDefinition = "VARCHAR(20) not null")
    private String status;

    private LocalDateTime completedAt;

    private LocalDateTime skippedAt;

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must not exceed 5")
    @Column(columnDefinition = "TINYINT")
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String reviewNote;

    private LocalDateTime correctedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

     @ManyToOne
     @JoinColumn(name = "visit_member_id", nullable = false)
     @JsonIgnore
     private VisitMember visitMember;

     @ManyToOne
     @JoinColumn(name = "ride_id", nullable = false)
     @JsonIgnore
     private Ride ride;

     @ManyToOne
     @JoinColumn(name = "recorded_by_account_id", nullable = false)
     @JsonIgnore
     private Account recordedByAccount;

    @ManyToOne
    @JoinColumn(name = "plan_item_id")
    @JsonIgnore
    private PlanItem planItem;

    @OneToOne
    @JoinColumn(name = "activity_session_id", unique = true)
    @JsonIgnore
    private ActivitySession activitySession;

}
