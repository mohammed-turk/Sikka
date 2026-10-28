package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VisitGroupDtoOut {
    private Integer id;
    private String name;
    private Integer organizerId;
    private Boolean invitationsEnabled;
    private LocalDateTime createdAt;
}