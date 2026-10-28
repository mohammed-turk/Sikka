package com.example.sikka.Repository;

import com.example.sikka.Model.VisitMealBreak;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VisitMealBreakRepository extends JpaRepository<VisitMealBreak, Integer> {

    VisitMealBreak findVisitMealBreakById(Integer id);

    List<VisitMealBreak> findVisitMealBreaksByVisitId(Integer visitId);

    List<VisitMealBreak> findVisitMealBreaksByVisitMemberId(Integer visitMemberId);

    List<VisitMealBreak> findVisitMealBreaksByVisitIdAndBreakKey(Integer visitId, String breakKey);
}