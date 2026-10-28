package com.example.sikka.Repository;

import com.example.sikka.Model.Visit;
import com.example.sikka.Model.VisitMember;
import com.example.sikka.Model.VisitorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VisitMemberRepository extends JpaRepository<VisitMember, Integer> {

    VisitMember findVisitMemberById(Integer id);
    VisitMember findVisitMemberByVisitAndVisitorProfile(Visit visit, VisitorProfile visitorProfile);
    long countByVisit(Visit visit);

    VisitMember findVisitMemberByVisit_IdAndVisitorProfile_Account_Id(Integer visitId, Integer accountId);

    List<VisitMember> findAllByVisit_IdAndRsvpStatus(Integer visitId, String rsvpStatus);

    List<VisitMember> findAllByVisit_Id(Integer visitId);

    List<VisitMember> findAllByVisitorProfile_Account_Id(Integer accountId);

    List<VisitMember> findAllByFamilyMember_Id(Integer familyMemberId);

}