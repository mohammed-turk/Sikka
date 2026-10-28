package com.example.sikka.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Size(max = 150, message = "Source place ID must not exceed 150 characters")
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Invalid source place ID")
    @Column(unique = true, length = 150)
    private String sourcePlaceId;

    @NotBlank(message = "Place name cannot be blank")
    @Size(max = 150, message = "Place name must not exceed 150 characters")
    @Column(nullable = false, length = 150)
    private String name;

    @NotBlank(message = "Place kind cannot be blank")
    @Pattern(regexp = "^(RESTAURANT|SHOP|MEETING_POINT|RESTROOM|PRAYER_ROOM|FIRST_AID|OTHER)$", message = "Place kind must be RESTAURANT, SHOP, MEETING_POINT, RESTROOM, PRAYER_ROOM, FIRST_AID or OTHER")
    @Column(nullable = false, length = 20)
    private String kind;

    @Pattern(regexp = "^(STEAM_TOWN|CITY_OF_THRILLS|TWILIGHT_GARDENS|DISCOVERY_SPRINGS|GRAND_EXPOSITION|VALLEY_OF_FORTUNE|CITADEL)$", message = "Invalid land")
    @Column(length = 60)
    private String land;

    @Pattern(regexp = "^(FULL_SERVICE|FAST_FOOD|BUFFETERIA|KIOSK|CART|VIP_LOUNGE)$", message = "Invalid serviceType")
    @Column(length = 60)
    private String serviceType;

    @ElementCollection
    @Column(length = 60)
    private Set<@Pattern(regexp = "^(NORTH_AMERICAN|MIDDLE_EASTERN|MEXICAN|INDIAN|ITALIAN|PAN_ASIAN|MULTINATIONAL)$", message = "Invalid cuisineTypes value") String> cuisineTypes = new HashSet<>();

    @ElementCollection
    @Column(length = 60)
    private Set<@Pattern(regexp = "^(FLAGSHIP|LAND_STORE|RIDE_STORE|PHOTO_STORE|SPECIALTY_STORE|KIOSK|CART)$", message = "Invalid shopTypes value") String> shopTypes = new HashSet<>();

    @Pattern(regexp = "^(OPEN|CLOSED|UNAVAILABLE)$", message = "Invalid status")
    @Column(length = 20)
    private String status;

    @Column(length = 60)
    private String availabilitySource;

    @Size(max = 500, message = "Source URL must not exceed 500 characters")
    @Column(length = 500)
    private String sourceUrl;

    @PastOrPresent(message = "Details verification cannot be in the future")
    private LocalDateTime detailsVerifiedAt;

    @Digits(integer = 4, fraction = 6, message = "Invalid map X coordinate")
    @Column(precision = 10, scale = 6)
    private BigDecimal mapX;

    @Digits(integer = 4, fraction = 6, message = "Invalid map Y coordinate")
    @Column(precision = 10, scale = 6)
    private BigDecimal mapY;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "place")
    @JsonIgnore
    private Set<PlanItem> planItems = new HashSet<>();

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}
