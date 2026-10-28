package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VisitMemberDtoOut {
    private Integer id;
    private Integer visitorProfileId;
    private String name;
    private String rsvpStatus;
    private LocalDateTime checkedInAt;
    private Integer familyMemberId;

    public VisitMemberDtoOut(Integer id, Integer visitorProfileId, String name, String rsvpStatus, LocalDateTime checkedInAt) {
        this(id, visitorProfileId, name, rsvpStatus, checkedInAt, null);
    }
}