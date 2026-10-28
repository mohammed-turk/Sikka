package com.example.sikka.DtoOut;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AiPlanItemDtoOut {

    @NotBlank(message = "Activity kind is required")
    @Pattern(regexp = "^(RIDE|PLACE|BREAK|MEETING_POINT)$", message = "Invalid activity kind")
    private String activityKind;

    @Positive(message = "Ride ID must be positive")
    private Integer rideId;

    @Positive(message = "Place ID must be positive")
    private Integer placeId;

    @NotNull(message = "Relative start is required")
    @PositiveOrZero(message = "Relative start must be zero or greater")
    private Integer relativeStartMinutes;

    @NotNull(message = "Relative end is required")
    @Positive(message = "Relative end must be positive")
    private Integer relativeEndMinutes;

    @NotNull(message = "Walking minutes are required")
    @PositiveOrZero(message = "Walking minutes must be zero or greater")
    private Integer walkingMinutes;

    @NotNull(message = "Waiting minutes are required")
    @PositiveOrZero(message = "Waiting minutes must be zero or greater")
    private Integer waitingMinutes;

    @NotNull(message = "Activity minutes are required")
    @Positive(message = "Activity minutes must be positive")
    private Integer activityMinutes;

    @Size(max = 20, message = "Break type must not exceed 20 characters")
    private String breakType;

    @NotEmpty(message = "Activity participants are required")
    private List<@NotNull @Positive Integer> participantVisitMemberIds;

    @NotBlank(message = "Subgroup key is required")
    @Size(max = 50, message = "Subgroup key must not exceed 50 characters")
    private String subgroupKey;

    @JsonIgnore
    private Integer executionEndMinutes;
}