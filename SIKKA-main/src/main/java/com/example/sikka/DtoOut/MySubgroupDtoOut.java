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
public class MySubgroupDtoOut {

    private Integer visitId;

    private String subgroupStatus;

    private String subgroupKey;

    private PlanItemDtoOut activity;

    private List<SubgroupMemberDtoOut> members;
}
