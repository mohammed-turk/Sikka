package com.example.sikka.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class ActivitySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "Activity status is required")
    @Pattern(regexp = "^(WAITING|ACTIVE|COMPLETED|SKIPPED)$", message = "Activity status must be WAITING, ACTIVE, COMPLETED or SKIPPED")
    @Column(nullable = false, length = 20)
    private String status;

    @NotBlank(message = "Tracking mode is required")
    @Pattern(regexp = "^(GPS|MANUAL)$", message = "Tracking mode must be GPS or MANUAL")
    @Column(nullable = false, length = 10)
    private String trackingMode = "MANUAL";

    private LocalDateTime enteredAreaAt;

    @JsonIgnore
    private LocalDateTime areaCandidateSince;

    @JsonIgnore
    @PositiveOrZero
    private Integer areaCandidateReadings;

    @JsonIgnore
    @Column(columnDefinition = "TEXT")
    private String areaCandidateDefinition;

    private LocalDateTime queueStartedAt;

    private LocalDateTime completedAt;

    @PositiveOrZero(message = "Expected wait minutes must be zero or greater")
    private Integer expectedWaitMinutes;

    @PositiveOrZero(message = "Expected ride minutes must be zero or greater")
    private Integer expectedRideMinutes;

    @PositiveOrZero(message = "Buffer minutes must be zero or greater")
    private Integer bufferMinutes;

    @DecimalMin(value = "-90.0", message = "Latitude must be at least -90")
    @DecimalMax(value = "90.0", message = "Latitude must not exceed 90")
    private Double lastLatitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be at least -180")
    @DecimalMax(value = "180.0", message = "Longitude must not exceed 180")
    private Double lastLongitude;

    @PositiveOrZero(message = "Location accuracy must be zero or greater")
    private Double lastAccuracyMeters;

    private LocalDateTime lastLocationAt;

    @Size(max = 150, message = "Alert key must not exceed 150 characters")
    @Column(length = 150)
    private String lastAlertKey;

    @Size(max = 500, message = "Delay reason must not exceed 500 characters")
    @Column(length = 500)
    private String delayReason;

    private LocalDateTime delayRespondedAt;

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must not exceed 5")
    private Integer rating;

    @Size(max = 1000, message = "Review note must not exceed 1000 characters")
    @Column(length = 1000)
    private String reviewNote;

    @ManyToOne
    @JoinColumn(name = "visit_member_id", nullable = false)
    @JsonIgnore
    private VisitMember visitMember;

    @ManyToOne
    @JoinColumn(name = "plan_item_id", nullable = false)
    @JsonIgnore
    private PlanItem planItem;

    @ManyToOne
    @JoinColumn(name = "ride_id")
    @JsonIgnore
    private Ride ride;

    @ManyToOne
    @JoinColumn(name = "place_id")
    @JsonIgnore
    private Place place;
}