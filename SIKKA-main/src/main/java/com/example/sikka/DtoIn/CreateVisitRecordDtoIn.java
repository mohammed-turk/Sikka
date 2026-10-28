package com.example.sikka.DtoIn;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreateVisitRecordDtoIn {

    @NotBlank(message = "Request key is required")
    @Size(max = 80, message = "Request key must not exceed 80 characters")
    private String requestKey;

    @NotNull(message = "Ride ID is required")
    @Positive(message = "Ride ID must be positive")
    private Integer rideId;

    @NotEmpty(message = "Select at least one visit member")
    private List<@NotNull(message = "Visit member ID is required") @Positive(message = "Visit member ID must be positive") Integer> visitMemberIds;

    @Positive(message = "Plan item ID must be positive")
    private Integer planItemId;

    @Positive(message = "Activity session ID must be positive")
    private Integer activitySessionId;


    @JsonIgnore
    @AssertTrue(message = "Plan item ID or activity session ID is required")
    public boolean isActivityReferenceProvided() {
        return planItemId != null || activitySessionId != null;
    }
}