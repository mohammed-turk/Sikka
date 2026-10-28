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
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "Ride name cannot be blank")
    @Size(max = 150, message = "Ride name must not exceed 150 characters")
    @Column(nullable = false, length = 150)
    private String name;

    @Size(max = 150, message = "Source ride ID must not exceed 150 characters")
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Invalid source ride ID")
    @Column(unique = true, length = 150)
    private String sourceRideId;

    @Pattern(regexp = "^(THRILL|FAMILY|KIDS|WATER|ARCADE)$", message = "Category must be THRILL, FAMILY, KIDS, WATER or ARCADE")
    @Size(max = 60, message = "Category must not exceed 60 characters")
    @Column(length = 60)
    private String category;

    @Pattern(regexp = "^(LOW|MILD|EXTREME)$", message = "Thrill level must be LOW, MILD or EXTREME" )
    @Column(length = 20)
    private String thrillLevel;

    @Pattern(regexp = "^(STEAM_TOWN|CITY_OF_THRILLS|TWILIGHT_GARDENS|DISCOVERY_SPRINGS|GRAND_EXPOSITION|VALLEY_OF_FORTUNE|CITADEL)$", message = "Invalid land")
    @Column(length = 60)
    private String land;

    @ElementCollection
    @Column(length = 60)
    private Set<@Pattern(regexp = "^(ROLLER_COASTER|FLAT_RIDE|DROP_TOWER|DARK_RIDE|WATER_RIDE|INTERACTIVE|PLAYGROUND|RECORD_BREAKING|ARCADE_CARNIVAL)$", message = "Invalid rideFeatures value") String> rideFeatures = new HashSet<>();

    // Null means unknown; false means verified that no adult companion is required.
    private Boolean adultCompanionRequired;

    @Positive(message = "Companion height threshold must be positive")
    @Digits(integer = 3, fraction = 2, message = "Invalid companion height threshold")
    @Column(precision = 5, scale = 2)
    private BigDecimal companionRequiredBelowHeightCm;

    @PositiveOrZero(message = "Recommended minimum age cannot be negative")
    private Integer recommendedMinAgeYears;

    @PositiveOrZero(message = "Recommended unaccompanied age cannot be negative")
    private Integer recommendedUnaccompaniedAgeYears;

    @PastOrPresent(message = "Requirements verification cannot be in the future")
    private LocalDateTime requirementsVerifiedAt;

    @NotBlank(message = "Status cannot be blank")
    @Pattern(regexp = "^(OPEN|CLOSED|UNAVAILABLE)$", message = "Status must be OPEN, CLOSED or UNAVAILABLE" )
    @Column(nullable = false, length = 20)
    private String status = "UNAVAILABLE";

    @Column(length = 60)
    private String availabilitySource;

    @Positive
    @Column(unique = true)
    private Long queueAnalyticsRideId;

    @JsonIgnore
    @Column(columnDefinition = "json")
    private String analyticsPatternsJson;

    private LocalDateTime analyticsPatternsFetchedAt;

    @PositiveOrZero(message = "Minimum height cannot be negative")
    @Digits(integer = 3, fraction = 2, message = "Invalid minimum height")
    @Column(precision = 5, scale = 2)
    private BigDecimal minHeightCm;

    @PositiveOrZero(message = "Maximum height cannot be negative")
    @Digits(integer = 3, fraction = 2, message = "Invalid maximum height")
    @Column(precision = 5, scale = 2)
    private BigDecimal maxHeightCm;

    @PositiveOrZero(message = "Minimum age cannot be negative")
    private Integer minAgeYears;

    @PositiveOrZero(message = "Maximum age cannot be negative")
    private Integer maxAgeYears;

    @PositiveOrZero(message = "Minimum weight cannot be negative")
    @Digits(integer = 4, fraction = 2, message = "Invalid minimum weight")
    @Column(precision = 6, scale = 2)
    private BigDecimal minWeightKg;

    @PositiveOrZero(message = "Maximum weight cannot be negative")
    @Digits(integer = 4, fraction = 2, message = "Invalid maximum weight")
    @Column(precision = 6, scale = 2)
    private BigDecimal maxWeightKg;

    @Column(columnDefinition = "TEXT")
    private String warnings;

    @PositiveOrZero(message = "Waiting time cannot be negative")
    private Integer waitMinutes;

    @PastOrPresent(message = "Waiting time observation cannot be in the future")
    private LocalDateTime waitObservedAt;

    @Size(max = 500, message = "Source URL must not exceed 500 characters")
    @Column(length = 500)
    private String sourceUrl;

    @Size(max = 255, message = "Wait source must not exceed 255 characters")
    @Column(length = 255)
    private String waitSource;

    @Digits(integer = 4, fraction = 6, message = "Invalid map X coordinate")
    @Column(precision = 10, scale = 6)
    private BigDecimal mapX;

    @Digits(integer = 4, fraction = 6, message = "Invalid map Y coordinate")
    @Column(precision = 10, scale = 6)
    private BigDecimal mapY;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Pattern(regexp = "^(UNKNOWN|NONE|SPECIFIED)$", message = "Invalid height rule status")
    @Column(length = 15)
    private String heightRuleStatus = "UNKNOWN";

    @Pattern(regexp = "^(UNKNOWN|NONE|SPECIFIED)$", message = "Invalid weight rule status")
    @Column(length = 15)
    private String weightRuleStatus = "UNKNOWN";

    @Pattern(regexp = "^(UNKNOWN|NONE|SPECIFIED)$", message = "Invalid age rule status")
    @Column(length = 15)
    private String ageRuleStatus = "UNKNOWN";

    @Pattern(
            regexp = "^(UNKNOWN|NONE|SPECIFIED)$",
            message = "Health rule status must be UNKNOWN, NONE or SPECIFIED"
    )
    @Column(nullable = false, columnDefinition = "varchar(20) default 'UNKNOWN'")
    private String healthRuleStatus = "UNKNOWN";

    @ElementCollection
    @CollectionTable(name = "ride_health_restrictions", joinColumns = @JoinColumn(name = "ride_id"))
    @Column(name = "condition_code", nullable = false, length = 60)
    private Set<@NotBlank @Size(max = 60) String> healthRestrictions = new HashSet<>();

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean additionalSafetyReviewRequired = true;

    @OneToMany(mappedBy = "ride")
    @JsonIgnore
    private Set<PlanItem> planItems = new HashSet<>();

    @OneToMany(mappedBy = "ride")
    @JsonIgnore
    private Set<VisitRecord> visitRecords = new HashSet<>();

    @OneToMany(mappedBy = "ride")
    @JsonIgnore
    private Set<RidePreference> ridePreferences = new HashSet<>();

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }

    @jakarta.validation.constraints.Positive
    private Integer rideDurationMinutes;

    @jakarta.validation.constraints.PositiveOrZero
    private Integer entryExitBufferMinutes;

    @Column(length = 60)
    private String timingSource;

    @Column(columnDefinition = "json")
    private String trackingArea;

}
