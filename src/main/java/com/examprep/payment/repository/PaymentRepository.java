package com.examprep.payment.repository;

import com.examprep.payment.entity.Payment;
import com.examprep.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Row lock (SELECT ... FOR UPDATE). The client "verify" call and the provider
     * webhook often arrive at the same moment; this serialises them so the enrollment
     * and the success email happen exactly once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.providerOrderId = :orderId")
    Optional<Payment> lockByProviderOrderId(@Param("orderId") String orderId);

    Optional<Payment> findFirstByUserIdAndSeriesIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID userId, UUID seriesId, PaymentStatus status, Instant after);

    List<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("""
            select p from Payment p
            where (:status is null or p.status = :status)
              and (:userId is null or p.userId = :userId)
              and (:seriesId is null or p.seriesId = :seriesId)
            """)
    Page<Payment> search(@Param("status") PaymentStatus status, @Param("userId") UUID userId,
                         @Param("seriesId") UUID seriesId, Pageable pageable);

    /**
     * Webhook de-duplication. Returns 1 the first time an event id is seen and 0 for
     * provider retries. It runs in the same transaction as the processing, so a failed
     * processing run also rolls back the "seen" mark and the retry is processed again.
     */
    @Modifying
    @Query(value = """
            INSERT INTO payment_events (provider, provider_event_id, event_type, payload)
            VALUES (:provider, :eventId, :eventType, CAST(:payload AS jsonb))
            ON CONFLICT (provider, provider_event_id) DO NOTHING
            """, nativeQuery = true)
    int recordEvent(@Param("provider") String provider, @Param("eventId") String eventId,
                    @Param("eventType") String eventType, @Param("payload") String payload);
}
