package com.example.sikka.DtoIn;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CompleteFamilyActivityDtoIn {

    @NotBlank(message = "Request key is required")
    @Size(max = 80, message = "Request key must not exceed 80 characters")
    private String requestKey;

    @NotEmpty(message = "Select at least one family participant")
    private List<@NotNull(message = "Participant ID is required") @Positive(message = "Participant ID must be positive") Integer> visitMemberIds;
}