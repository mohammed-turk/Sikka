package com.example.sikka.DtoIn;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateVisitRecordDtoIn {

    @NotNull(message = "Visit record ID is required")
    @Positive(message = "Visit record ID must be positive")
    private Integer visitRecordId;

}