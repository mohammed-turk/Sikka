package com.example.sikka.DtoIn;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VisitMemberDtoIn {

    @NotNull(message = "visit id cannot be null")
    private Integer visitId;

    @NotNull(message = "visitor profile id cannot be null")
    private Integer visitorProfileId;
}