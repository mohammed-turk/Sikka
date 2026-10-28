package com.example.sikka.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VisitMealBreak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "Break key is required")
    @Size(max = 80, message = "Break key must not exceed 80 characters")
    @Column(nullable = false, length = 80)
    private String breakKey;

    @NotBlank(message = "Meal choice is required")
    @Pattern(
            regexp = "^(MEAL|SNACK|DEFERRED|SKIPPED|NONE)$",
            message = "Meal choice must be MEAL, SNACK, DEFERRED, SKIPPED or NONE"
    )
    @Column(nullable = false, length = 20)
    private String mealChoice;

    @Pattern(
            regexp = "^(START|MIDDLE|END)$",
            message = "Preferred timing must be START, MIDDLE or END"
    )
    @Column(length = 20)
    private String preferredTiming;

    @ManyToOne
    @JoinColumn(name = "visit_id", nullable = false)
    @JsonIgnore
    private Visit visit;

    @ManyToOne
    @JoinColumn(name = "visit_member_id", nullable = false)
    @JsonIgnore
    private VisitMember visitMember;

    @ManyToOne
    @JoinColumn(name = "place_id")
    @JsonIgnore
    private Place place;

    @ManyToOne
    @JoinColumn(name = "plan_item_id")
    @JsonIgnore
    private PlanItem planItem;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}