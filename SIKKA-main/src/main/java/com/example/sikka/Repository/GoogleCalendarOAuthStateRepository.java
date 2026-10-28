package com.example.sikka.Repository;

import com.example.sikka.Model.GoogleCalendarOAuthState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

public interface GoogleCalendarOAuthStateRepository extends JpaRepository<GoogleCalendarOAuthState, String> {
    @Modifying
    @Transactional
    @Query("UPDATE GoogleCalendarOAuthState s SET s.consumed = true WHERE s.stateHash = :hash AND s.consumed = false AND s.expiresAt > :now")
    int consume(@Param("hash") String hash, @Param("now") LocalDateTime now);
}
