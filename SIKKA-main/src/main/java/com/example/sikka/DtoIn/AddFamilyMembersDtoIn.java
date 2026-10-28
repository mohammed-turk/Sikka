package com.example.sikka.DtoIn;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddFamilyMembersDtoIn {
    @NotEmpty
    private List<@NotNull @Positive Integer> familyMemberIds;

    @Positive
    private Integer maxParticipants;

    public AddFamilyMembersDtoIn(List<Integer> familyMemberIds) {
        this.familyMemberIds = familyMemberIds;
    }
}
