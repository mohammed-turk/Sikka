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
public class MapRouteDtoOut {

    private String fromType;
    private Integer fromId;

    private String toType;
    private Integer toId;

    private Double distanceMeters;

    // Each point contains [x, y] on the park image.
    private List<List<Double>> points;
}