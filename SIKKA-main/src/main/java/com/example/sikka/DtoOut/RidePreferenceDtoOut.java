package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RidePreferenceDtoOut {

    private Integer id;

    private Integer rideId;

    private String rideName;

    private String preference;
}
