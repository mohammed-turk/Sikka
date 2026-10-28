package com.example.sikka.DtoIn;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemberPreferencesDtoIn {

    @Valid
    private List<@NotNull SelectedRideDtoIn> selectedRides;

    private List<@NotBlank String> dislikedFeatures;

    private List<@Positive Integer> excludedRideIds;

    private List<@NotBlank String> excludedRideTypes;

    public MemberPreferencesDtoIn(List<SelectedRideDtoIn> selectedRides, List<String> dislikedFeatures) {
        this.selectedRides = selectedRides;
        this.dislikedFeatures = dislikedFeatures;
    }
}
