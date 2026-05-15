package com.nsemcx.trading.repository;

import com.nsemcx.trading.model.Alert;
import com.nsemcx.trading.model.Alert.AlertSeverity;
import com.nsemcx.trading.model.Alert.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByIsTriggeredOrderByCreatedAtDesc(Boolean isTriggered);

    List<Alert> findBySeverityOrderByCreatedAtDesc(AlertSeverity severity);

    List<Alert> findByIsTriggeredAndSeverityOrderByCreatedAtDesc(Boolean isTriggered, AlertSeverity severity);

    List<Alert> findByIsReadFalseOrderByCreatedAtDesc();

    List<Alert> findByInstrumentIdOrderByCreatedAtDesc(Long instrumentId);

    List<Alert> findByAlertTypeOrderByCreatedAtDesc(AlertType alertType);

    @Query("SELECT a FROM Alert a WHERE a.isTriggered = true AND a.isRead = false ORDER BY a.triggeredAt DESC")
    List<Alert> findUnreadTriggeredAlerts();

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.isRead = false")
    long countUnread();

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.isTriggered = true AND a.isRead = false")
    long countUnreadTriggered();

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.severity = :severity AND a.isRead = false")
    long countUnreadBySeverity(@Param("severity") AlertSeverity severity);

    @Modifying
    @Transactional
    @Query("UPDATE Alert a SET a.isRead = true WHERE a.id = :id")
    int markAsRead(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE Alert a SET a.isRead = true WHERE a.isRead = false")
    int markAllAsRead();

    @Query("SELECT a FROM Alert a WHERE a.instrument.id = :instrumentId AND a.isTriggered = false ORDER BY a.createdAt DESC")
    List<Alert> findPendingAlertsByInstrumentId(@Param("instrumentId") Long instrumentId);

    @Query("SELECT a FROM Alert a ORDER BY a.createdAt DESC LIMIT :limit")
    List<Alert> findRecent(@Param("limit") int limit);
}
