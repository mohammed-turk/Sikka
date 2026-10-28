package com.example.sikka.DtoIn;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VisitGroupDtoIn {

    @NotNull(message = "organizer id cannot be null")
    private Integer organizerId;

    @NotEmpty(message = "group name is required")
    @Size(max = 150, message = "group name must not exceed 150 characters")
    private String name;

    private Boolean invitationsEnabled;
}