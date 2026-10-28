package com.example.sikka.DtoIn;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateVisitDtoIn {

    @Size(max = 150, message = "group name must not exceed 150 characters")
    private String groupName;

    @Size(max = 150, message = "name must not exceed 150 characters")
    private String name;

    @NotNull(message = "visit date is required")
    @FutureOrPresent(message = "visit date cannot be in the past")
    private LocalDate visitDate;

    private LocalTime plannedStartTime;

    private LocalTime plannedEndTime;


    @Positive(message = "max participants must be positive")
    private Integer maxParticipants;

    @NotNull(message = "trip type is required")
    @Pattern(regexp = "^(SOLO|FRIENDS|FAMILY)$", message = "trip type must be SOLO, FRIENDS or FAMILY")
    private String tripType;

    @Pattern(regexp = "^(TOGETHER|SPLIT)$", message = "group mode must be TOGETHER or SPLIT")
    private String togetherMode;

    private String goals;

    @Size(max = 50, message = "top goal must not exceed 50 characters")
    private String primaryGoal;

    private Boolean includePrayer;

    @Positive(message = "family count must be positive")
    private Integer familyCount;

    @Positive(message = "Visit duration must be positive")
    private Integer durationMinutes;

}