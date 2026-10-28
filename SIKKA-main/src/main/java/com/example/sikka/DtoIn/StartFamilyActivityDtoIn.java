package com.example.sikka.DtoIn;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StartFamilyActivityDtoIn {

    @NotEmpty(message = "Select at least one family participant")
    private List<@NotNull(message = "Participant ID is required") @Positive(message = "Participant ID must be positive") Integer> visitMemberIds;
}