package com.example.sikka.Repository;

import com.example.sikka.Model.Notification;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    Notification findNotificationById(Integer id);

    Notification findNotificationByAccount_IdAndChannelAndEventKey(Integer accountId, String channel, String eventKey);

    List<Notification> findAllByAccount_IdAndChannelAndStatusOrderByCreatedAtDescIdDesc(Integer accountId, String channel, String status);

    long countByAccount_IdAndChannelAndStatusAndReadAtIsNull(Integer accountId, String channel, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notification n where n.id = :notificationId")
    Notification findNotificationByIdForUpdate(@Param("notificationId") Integer notificationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notification n where n.channel = 'IN_APP' and n.status = 'PENDING' and (n.scheduledAt is null or n.scheduledAt <= :now) order by n.id asc")
    List<Notification> findDueInAppNotificationsForUpdate(@Param("now") LocalDateTime now, Pageable pageable);
}