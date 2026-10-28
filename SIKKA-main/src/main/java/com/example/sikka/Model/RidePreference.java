package com.example.sikka.Model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"visitor_profile_id", "ride_id"})
})
public class RidePreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "Preference cannot be blank")
    @Pattern(regexp = "^(REPEAT|AVOID|NEUTRAL)$",
            message = "Preference must be REPEAT, AVOID or NEUTRAL"
    )
    @Column(nullable = false, length = 10)
    private String preference;

    @ManyToOne
    @JoinColumn(name = "visitor_profile_id", nullable = false)
    @JsonIgnore
    private VisitorProfile visitorProfile;

    @ManyToOne
    @JoinColumn(name = "ride_id", nullable = false)
    @JsonIgnore
    private Ride ride;
}