package com.example.sikka.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class PlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "Activity kind is required")
    @Pattern(regexp = "^(RIDE|PLACE|BREAK|MEETING_POINT)$", message = "Activity kind must be RIDE, PLACE, BREAK or MEETING_POINT")
    @Column(columnDefinition = "VARCHAR(20) not null")
    private String activityKind;

    @Size(max = 150, message = "Title must not exceed 150 characters")
    @Column(columnDefinition = "VARCHAR(150)")
    private String title;

    @PositiveOrZero(message = "Relative start must be zero or greater")
    private Integer relativeStartMinutes;

    @PositiveOrZero(message = "Relative end must be zero or greater")
    private Integer relativeEndMinutes;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    @JsonIgnore
    private LocalDateTime executionReleasedAt;

    @PositiveOrZero(message = "Walking minutes must be zero or greater")
    private Integer walkingMinutes;

    @PositiveOrZero(message = "Waiting minutes must be zero or greater")
    private Integer waitingMinutes;

    @PositiveOrZero(message = "Activity minutes must be zero or greater")
    private Integer activityMinutes;

    @Size(max = 20, message = "Break type must not exceed 20 characters")
    @Column(columnDefinition = "VARCHAR(20)")
    private String breakType;

    @NotNull(message = "Rateable status is required")
    @Column(name = "is_rateable", columnDefinition = "BOOLEAN not null default false")
    private Boolean isRateable = false;

    @NotNull(message = "Sort order is required")
    @PositiveOrZero(message = "Sort order must be zero or greater")
    @Column(columnDefinition = "INT not null")
    private Integer sortOrder;

    @Pattern(regexp = "^(MUST_RIDE|PREFERRED|OPTIONAL)$", message = "Priority must be MUST_RIDE, PREFERRED or OPTIONAL")
    @Column(columnDefinition = "VARCHAR(20)")
    private String priority;

    @NotNull(message = "Pinned status is required")
    @Column(columnDefinition = "BOOLEAN not null")
    private Boolean pinned = false;

    @Column(columnDefinition = "JSON")
    private String participantVisitMemberIds;

    @Size(max = 50, message = "Subgroup key must not exceed 50 characters")
    @Column(columnDefinition = "VARCHAR(50)")
    private String subgroupKey;

    @ManyToOne
    @JoinColumn(name = "visit_plan_id", nullable = false)
    @JsonIgnore
    private VisitPlan visitPlan;

    @ManyToOne
    @JoinColumn(name = "ride_id")
    @JsonIgnore
    private Ride ride;

    @ManyToOne
    @JoinColumn(name = "place_id")
    @JsonIgnore
    private Place place;
}