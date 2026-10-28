package com.example.sikka.Repository;

import com.example.sikka.Model.GoogleCalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoogleCalendarEventRepository extends JpaRepository<GoogleCalendarEvent, Integer> {
    Optional<GoogleCalendarEvent> findByAccount_IdAndVisit_Id(Integer accountId, Integer visitId);
    java.util.List<GoogleCalendarEvent> findAllByVisit_Id(Integer visitId);
    void deleteAllByAccount_Id(Integer accountId);
}
