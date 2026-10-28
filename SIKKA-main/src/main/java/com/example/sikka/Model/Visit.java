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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(columnDefinition = "varchar(150)")
    private String name;

    @Column(columnDefinition = "date not null")
    private LocalDate visitDate;

    @Column(columnDefinition = "int not null")
    private Integer maxParticipants;

    @Column(columnDefinition = "time")
    private LocalTime plannedStartTime;

    @Column(columnDefinition = "time")
    private LocalTime plannedEndTime;

    @Column(columnDefinition = "varchar(10)")
    private String tripType;

    @Column(name = "group_mode", columnDefinition = "varchar(10)")
    private String togetherMode;

    @Column(columnDefinition = "json")
    private String goals;

    @Column(name = "top_goal", columnDefinition = "varchar(50)")
    private String primaryGoal;

    @Column(name = "prayer_enabled", columnDefinition = "boolean not null default false")
    private Boolean includePrayer = false;

    @Column(columnDefinition = "time")
    private LocalTime expectedArrivalTime;

    @Pattern(regexp = "^(DRAFT|PLANNING|READY|IN_PROGRESS|COMPLETED|CANCELLED)$",
            message = "invalid visit status")
    @Column(columnDefinition = "varchar(20) not null default 'DRAFT'")
    private String status;

    @Column(columnDefinition = "boolean not null default false")
    private Boolean membersLocked = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToOne
    @JoinColumn(name = "group_id", unique = true)
    @JsonIgnore
    private VisitGroup group;

    @ManyToOne
    @JoinColumn(name = "organizer_account_id", nullable = false)
    @JsonIgnore
    private Account organizer;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "visit")
    @JsonIgnore
    private Set<VisitMember> members;

    @OneToMany(mappedBy = "visit")
    @JsonIgnore
    private Set<VisitPlan> visitPlans;

    @OneToMany(mappedBy = "visit")
    @JsonIgnore
    private Set<Notification> notifications;

    @OneToMany(mappedBy = "visit")
    @JsonIgnore
    private Set<PaymentOrder> paymentOrders;

    @OneToMany(mappedBy = "visit")
    @JsonIgnore
    private Set<PlanHistory> planHistories;

    @Positive
    private Integer durationMinutes;

    @Column(nullable = false)
    private Boolean freeSlotReserved = false;

    @Column(name = "reused_from_visit_id")
    private Integer reusedFromVisitId;

    private LocalDateTime startedAt;

    @Column(columnDefinition = "json")
    private String sharedSelectedRides;

    public Integer getOrganizerAccountId() {
        return organizer == null ? null : organizer.getId();
    }

}
