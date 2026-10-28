package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VisitMembersSummaryDtoOut {
    private Integer total;
    private Integer goingCount;
    private List<VisitMemberDtoOut> members;
}