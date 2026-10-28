package com.example.sikka.Repository;

import com.example.sikka.Model.Account;
import com.example.sikka.Model.VisitRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VisitRecordRepository extends JpaRepository<VisitRecord, Integer> {

    VisitRecord findVisitRecordById(Integer id);

    boolean existsByVisitMember_IdAndRide_IdAndStatus(Integer memberId, Integer rideId, String status);

    List<VisitRecord> findAllByVisitMember_IdOrderByCreatedAtDescIdDesc(Integer visitMemberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from VisitRecord r where r.id = :visitRecordId")
    VisitRecord findVisitRecordByIdForUpdate(@Param("visitRecordId") Integer visitRecordId);

    List<VisitRecord> findAllByRecordedByAccount_IdAndRequestKeyOrderByIdAsc(Integer accountId, String requestKey);

    VisitRecord findVisitRecordByVisitMember_IdAndPlanItem_Id(Integer visitMemberId, Integer planItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :accountId")
    Account findRecordingAccountByIdForUpdate(@Param("accountId") Integer accountId);

    boolean existsByVisitMember_Id(Integer visitMemberId);
}