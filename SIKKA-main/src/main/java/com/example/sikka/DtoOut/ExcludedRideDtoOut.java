package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ExcludedRideDtoOut {
    private Integer rideId;
    private String rideName;
    private Integer visitMemberId;
    private String priority;
    private String reasonCode;
    private String reasonMessage;
}
