package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RideWaitTimeDtoOut {

    private Integer rideId;

    private String rideName;

    private String status;

    private Integer waitMinutes;

    private LocalDateTime waitObservedAt;

    private String waitSource;

    private String freshness;
}
