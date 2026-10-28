package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ReuseVisitDtoOut {

    private Integer visitId;

    private Integer sourceVisitId;

    private String status;

    private String inviteCode;

    private List<String> warnings;
}