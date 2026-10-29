package com.homefix.api.repository;

import com.homefix.api.model.PaymentRefund;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {
    Optional<PaymentRefund> findByIdempotencyKey(String idempotencyKey);
    Optional<PaymentRefund> findByGatewayRefundId(String gatewayRefundId);
    List<PaymentRefund> findAllByTransaction_IdOrderByCreatedAtDesc(Long transactionId);
    List<PaymentRefund> findAllByTransaction_Booking_IdOrderByCreatedAtDesc(Long bookingId);
    List<PaymentRefund> findAllByOrderByCreatedAtDesc();

    @Query("""
            select coalesce(sum(r.amount), 0)
            from PaymentRefund r
            where r.transaction.id = :transactionId
              and r.status in :statuses
            """)
    BigDecimal sumAmountsByTransactionAndStatuses(
            @Param("transactionId") Long transactionId,
            @Param("statuses") List<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PaymentRefund r where r.gatewayRefundId = :refundId")
    Optional<PaymentRefund> findByGatewayRefundIdForUpdate(@Param("refundId") String refundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PaymentRefund r where r.id = :refundId")
    Optional<PaymentRefund> findByIdForUpdate(@Param("refundId") Long refundId);
}
