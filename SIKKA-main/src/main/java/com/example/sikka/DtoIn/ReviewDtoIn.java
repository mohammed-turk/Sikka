package com.example.sikka.DtoIn;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReviewDtoIn {


    @Min(value = 1, message = "rating must be between 1 and 5")
    @Max(value = 5, message = "rating must be between 1 and 5")
    private Integer rating;

    private String review;

    @Min(value = 1, message = "sikka rating must be between 1 and 5")
    @Max(value = 5, message = "sikka rating must be between 1 and 5")
    private Integer sikkaRating;

    private String sikkaReview;
}