package com.example.sikka.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"visit_id", "visitor_profile_id"}))
public class VisitMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(columnDefinition = "varchar(100)")
    private String name;

    @Column(columnDefinition = "date")
    private LocalDate birthDate;

    @Column(columnDefinition = "decimal(5,2)")
    private BigDecimal heightCm;

    @Column(columnDefinition = "decimal(6,2)")
    private BigDecimal weightKg;

    @Column(columnDefinition = "varchar(10)")
    private String gender;

    @Pattern(regexp = "^(INVITED|GOING|MAYBE|NOT_GOING)$",
            message = "rsvp status must be INVITED, GOING, MAYBE, or NOT_GOING")
    @Column(columnDefinition = "varchar(15) not null default 'INVITED'")
    private String rsvpStatus;

    @Column(columnDefinition = "json")
    private String selectedRides;

    @Column(columnDefinition = "datetime")
    private LocalDateTime checkedInAt;


    @Column(columnDefinition = "tinyint")
    private Integer visitRating;

    @Column(columnDefinition = "text")
    private String visitReview;

    @Column(columnDefinition = "tinyint")
    private Integer sikkaRating;

    @Column(columnDefinition = "text")
    private String sikkaReview;

    @Column(columnDefinition = "datetime")
    private LocalDateTime invitedAt;

    @ManyToOne
    @JoinColumn(name = "visit_id", nullable = false)
    @JsonIgnore
    private Visit visit;

    @ManyToOne
    @JoinColumn(name = "visitor_profile_id")
    @JsonIgnore
    private VisitorProfile visitorProfile;

    @OneToMany(mappedBy = "ownerVisitMember")
    @JsonIgnore
    private Set<VisitPlan> visitPlans;

    @OneToMany(mappedBy = "visitMember")
    @JsonIgnore
    private Set<VisitRecord> visitRecords;



    @ManyToOne
    @JoinColumn(name = "family_member_id")
    @JsonIgnore
    private FamilyMember familyMember;

    @Pattern(regexp = "^(PENDING|ANSWERED|SKIPPED)$")
    @Column(columnDefinition = "varchar(15) not null default 'PENDING'")
    private String answerStatus = "PENDING";

    @Column(columnDefinition = "json")
    private String preferences;

    private LocalDateTime snapshotAt;

}