package com.example.sikka.DtoIn;

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
public class SuggestReplanDtoIn {

    @NotBlank(message = "Replanning reason is required")
    @Size(max = 500, message = "Replanning reason must not exceed 500 characters")
    private String reason;

    private Boolean replaceUnavailableRides = false;

    @Size(min = 1, message = "Affected member IDs must not be empty")
    private List<@NotNull(message = "Member ID is required") @Positive(message = "Member ID must be positive") Integer> affectedMemberIds;

    @JsonIgnore
    private Integer sourceDelayNotificationId;

    @JsonIgnore
    private String sourceDelayReason;
}