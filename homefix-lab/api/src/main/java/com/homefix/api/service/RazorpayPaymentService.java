package com.homefix.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homefix.api.model.AuditLog;
import com.homefix.api.model.BookingEntity;
import com.homefix.api.model.Notification;
import com.homefix.api.model.Payment;
import com.homefix.api.model.PaymentRefund;
import com.homefix.api.model.PaymentTransaction;
import com.homefix.api.model.PaymentWebhookEvent;
import com.homefix.api.model.User;
import com.homefix.api.repository.AuditLogRepository;
import com.homefix.api.repository.BookingRepository;
import com.homefix.api.repository.NotificationRepository;
import com.homefix.api.repository.PaymentRepository;
import com.homefix.api.repository.PaymentTransactionRepository;
import com.homefix.api.repository.PaymentWebhookEventRepository;
import com.homefix.api.repository.PaymentRefundRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.List;

@Service
public class RazorpayPaymentService {

    private static final String CURRENCY = "INR";
    private static final Set<String> ACTIVE_ORDER_STATUSES = Set.of("ORDER_CREATED", "AUTHORIZED");
    private static final List<String> REFUNDABLE_REFUND_STATUSES =
            List.of("PENDING", "PROCESSED");

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final RazorpayGatewayClient gatewayClient;
    private final ObjectMapper objectMapper;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentRefundRepository refundRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogRepository auditLogRepository;

    public RazorpayPaymentService(
            @Value("${app.payment.razorpay.key-id:}") String keyId,
            @Value("${app.payment.razorpay.key-secret:}") String keySecret,
            @Value("${app.payment.razorpay.webhook-secret:}") String webhookSecret,
            RazorpayGatewayClient gatewayClient,
            ObjectMapper objectMapper,
            BookingRepository bookingRepository,
            PaymentRepository paymentRepository,
            PaymentTransactionRepository transactionRepository,
            PaymentWebhookEventRepository webhookEventRepository,
            PaymentRefundRepository refundRepository,
            NotificationRepository notificationRepository,
            AuditLogRepository auditLogRepository) {
        this.keyId = keyId.trim();
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
        this.gatewayClient = gatewayClient;
        this.objectMapper = objectMapper;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.transactionRepository = transactionRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.refundRepository = refundRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public boolean isConfigured() {
        return keyId.startsWith("rzp_test_")
                && !keySecret.isBlank() && !webhookSecret.isBlank();
    }

    public String configurationStatus() {
        if (keyId.isBlank() && keySecret.isBlank() && webhookSecret.isBlank()) {
            return "Razorpay test credentials are not configured.";
        }
        if (!keyId.startsWith("rzp_test_")) {
            return "Checkout is disabled until a Razorpay test-mode key ID is configured.";
        }
        if (keySecret.isBlank() || webhookSecret.isBlank()) {
            return "All three Razorpay test credentials are required to enable checkout.";
        }
        return "Razorpay test mode is configured.";
    }

    public String publicKeyId() {
        return isConfigured() ? keyId : "";
    }

    public boolean isTestMode() {
        return keyId.startsWith("rzp_test_");
    }

    @Transactional
    public String cancelCheckout(Long transactionId, User customer) {
        PaymentTransaction transaction = transactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new PaymentOperationException(
                        HttpStatus.NOT_FOUND, "Payment attempt was not found."));
        requireTransactionOwner(transaction, customer);
        if ("CANCELLED".equals(transaction.getStatus())) {
            return "CANCELLED";
        }
        if (!"ORDER_CREATED".equals(transaction.getStatus())
                || transaction.getGatewayPaymentId() != null) {
            throw new PaymentOperationException(HttpStatus.CONFLICT,
                    "This checkout may still be processing and cannot be cancelled yet.");
        }
        transaction.setStatus("CANCELLED");
        transaction.setFailureReason("Checkout was cancelled by the customer.");
        transactionRepository.save(transaction);
        paymentRepository.findByBooking_IdForUpdate(transaction.getBooking().getId())
                .filter(payment -> !"PAID".equalsIgnoreCase(payment.getStatus()))
                .ifPresent(payment -> {
                    payment.setStatus("UNPAID");
                    payment.setAdminNote("The customer cancelled the gateway checkout.");
                    paymentRepository.save(payment);
                });
        return "CANCELLED";
    }

    @Transactional(noRollbackFor = PaymentOperationException.class)
    public PaymentRefund requestRefund(
            Long transactionId, User admin, String idempotencyKey,
            BigDecimal requestedAmount, String reason) {
        requireConfigured();
        PaymentTransaction transaction = transactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new PaymentOperationException(
                        HttpStatus.NOT_FOUND, "Payment transaction was not found."));
        PaymentRefund existing = refundRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.getTransaction().getId().equals(transactionId)
                    || existing.getAmount().compareTo(requestedAmount) != 0
                    || !existing.getReason().equals(reason.trim())) {
                throw new PaymentOperationException(HttpStatus.CONFLICT,
                        "This idempotency key was already used for a different refund request.");
            }
            return existing;
        }
        if (!"CAPTURED".equals(transaction.getStatus())
                && !"PARTIALLY_REFUNDED".equals(transaction.getStatus())) {
            throw new PaymentOperationException(HttpStatus.CONFLICT,
                    "Only captured payments with a refundable balance can be refunded.");
        }
        BigDecimal reservedRefunds = refundRepository.sumAmountsByTransactionAndStatuses(
                transactionId, REFUNDABLE_REFUND_STATUSES);
        BigDecimal refundableBalance = transaction.getAmount().subtract(reservedRefunds)
                .setScale(2);
        BigDecimal amount = requestedAmount.setScale(2, java.math.RoundingMode.UNNECESSARY);
        if (transaction.getGatewayPaymentId() == null
                || transaction.getGatewayPaymentId().isBlank()) {
            throw new PaymentOperationException(HttpStatus.CONFLICT,
                    "The captured gateway payment reference is unavailable for refund.");
        }
        if (amount.signum() <= 0 || amount.compareTo(refundableBalance) > 0) {
            throw new PaymentOperationException(HttpStatus.BAD_REQUEST,
                    "Refund amount exceeds the remaining refundable balance.");
        }
        PaymentRefund refund = new PaymentRefund();
        refund.setTransaction(transaction);
        refund.setRequestedByAdmin(admin);
        refund.setIdempotencyKey(idempotencyKey);
        refund.setAmount(amount);
        refund.setReason(reason.trim());
        refund = refundRepository.saveAndFlush(refund);
        try {
            JsonNode gatewayRefund = gatewayClient.createRefund(
                    keyId, keySecret, transaction.getGatewayPaymentId(), Map.of(
                            "amount", amount.movePointRight(2).longValueExact(),
                            "receipt", "hf-refund-" + refund.getId(),
                            "notes", Map.of("homefix_refund_id", refund.getId().toString())));
            if (gatewayRefund == null || !gatewayRefund.hasNonNull("id")
                    || gatewayRefund.path("amount").asLong(-1)
                    != amount.movePointRight(2).longValueExact()) {
                refund.setStatus("PENDING");
                refund.setFailureReason("Gateway response requires webhook reconciliation.");
            } else {
                refund.setGatewayRefundId(gatewayRefund.path("id").asText());
                String gatewayStatus = gatewayRefund.path("status").asText("");
                if (!gatewayRefund.path("payment_id").asText("")
                        .equals(transaction.getGatewayPaymentId())
                        || !Set.of("pending", "processed").contains(gatewayStatus.toLowerCase())) {
                    refund.setStatus("PENDING");
                    refund.setFailureReason("Gateway response requires webhook reconciliation.");
                    refundRepository.save(refund);
                    return refund;
                }
                refund.setStatus("processed".equalsIgnoreCase(gatewayStatus)
                        ? "PROCESSED" : "PENDING");
            }
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                refund.setStatus("FAILED");
                refund.setFailureReason("Razorpay rejected the refund request.");
            } else {
                refund.setStatus("PENDING");
                refund.setFailureReason(
                        "Gateway response is uncertain; reconcile by webhook before retrying.");
            }
        } catch (RestClientException exception) {
            refund.setStatus("PENDING");
            refund.setFailureReason(
                    "Gateway response is uncertain; reconcile by webhook before retrying.");
        }
        refund = refundRepository.save(refund);
        auditLogRepository.save(new AuditLog(
                admin, "PAYMENT_REFUND_REQUESTED", "PAYMENT_REFUND",
                refund.getId().toString(),
                "Refund requested for transaction " + transactionId
                        + ", amount " + amount.toPlainString() + "."));
        synchronizeRefundStatus(transaction);
        return refund;
    }

    @Transactional
    public List<PaymentRefundRefundView> customerRefunds(Long bookingId, User customer) {
        BookingEntity booking = bookingRepository.findById(bookingId)
                .filter(item -> item.getUser() != null
                        && item.getUser().getId().equals(customer.getId()))
                .orElseThrow(() -> new PaymentOperationException(
                        HttpStatus.NOT_FOUND, "Booking was not found for this account."));
        return refundRepository.findAllByTransaction_Booking_IdOrderByCreatedAtDesc(booking.getId())
                .stream().map(this::refundView).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> customerPaymentHistory(Long bookingId, User customer) {
        BookingEntity booking = bookingRepository.findById(bookingId)
                .filter(item -> item.getUser() != null
                        && item.getUser().getId().equals(customer.getId()))
                .orElseThrow(() -> new PaymentOperationException(
                        HttpStatus.NOT_FOUND, "Booking was not found for this account."));
        return transactionRepository.findAllByBooking_IdOrderByCreatedAtDesc(booking.getId())
                .stream()
                .map(transaction -> Map.<String, Object>ofEntries(
                        Map.entry("transactionId", transaction.getId()),
                        Map.entry("amount", transaction.getAmount()),
                        Map.entry("currency", transaction.getCurrency()),
                        Map.entry("status", transaction.getStatus()),
                        Map.entry("gatewayPaymentId", transaction.getGatewayPaymentId() == null
                                ? "" : transaction.getGatewayPaymentId()),
                        Map.entry("failureReason", transaction.getFailureReason() == null
                                ? "" : transaction.getFailureReason()),
                        Map.entry("createdAt", transaction.getCreatedAt()),
                        Map.entry("refunds", refundsForTransaction(transaction.getId()))
                ))
                .toList();
    }

    @Transactional
    public PaymentOrderResult createOrder(Long bookingId, User customer, String idempotencyKey) {
        requireConfigured();
        BookingEntity booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new PaymentOperationException(
                        HttpStatus.NOT_FOUND, "Booking was not found."));
        if (booking.getUser() == null || !booking.getUser().getId().equals(customer.getId())) {
            throw new PaymentOperationException(HttpStatus.NOT_FOUND,
                    "Booking was not found for this account.");
        }
        if (!"COMPLETED".equalsIgnoreCase(booking.getStatus())) {
            throw new PaymentOperationException(HttpStatus.CONFLICT,
                    "UPI checkout is available after the provider completes the booking.");
        }
        Payment payment = paymentRepository.findByBooking_IdForUpdate(bookingId)
                .orElseThrow(() -> new PaymentOperationException(
                        HttpStatus.CONFLICT, "Payment record is not available for this booking."));
        if (!"UPI".equalsIgnoreCase(payment.getMethod())) {
            throw new PaymentOperationException(HttpStatus.CONFLICT,
                    "This booking was not set up for UPI payment.");
        }
        if ("PAID".equalsIgnoreCase(payment.getStatus())) {
            throw new PaymentOperationException(HttpStatus.CONFLICT,
                    "This booking has already been paid.");
        }
        if (!Set.of("UNPAID", "FAILED", "PENDING").contains(payment.getStatus().toUpperCase())) {
            throw new PaymentOperationException(HttpStatus.CONFLICT,
                    "This booking is not currently eligible for payment.");
        }
        BigDecimal amount = payment.getAmount().setScale(2);
        if (amount.signum() <= 0 || !amount.equals(booking.getPrice().setScale(2))) {
            throw new PaymentOperationException(HttpStatus.CONFLICT,
                    "The saved payment amount does not match the approved booking price.");
        }

        PaymentTransaction transaction = transactionRepository
                .findByIdempotencyKey(idempotencyKey).orElse(null);
        if (transaction != null) {
            if (!transaction.getBooking().getId().equals(bookingId)
                    || !transaction.getCustomer().getId().equals(customer.getId())) {
                throw new PaymentOperationException(HttpStatus.CONFLICT,
                        "This idempotency key has already been used for another request.");
            }
            if (!transaction.getAmount().equals(amount)) {
                throw new PaymentOperationException(HttpStatus.CONFLICT,
                        "The booking amount changed; use a new payment attempt.");
            }
            if (ACTIVE_ORDER_STATUSES.contains(transaction.getStatus())) {
                return orderResult(transaction);
            }
            if (!"ORDER_FAILED".equals(transaction.getStatus())) {
                throw new PaymentOperationException(HttpStatus.CONFLICT,
                        "This payment attempt is no longer available.");
            }
        } else {
            transaction = transactionRepository
                    .findFirstByBooking_IdAndStatusOrderByCreatedAtDesc(bookingId, "ORDER_CREATED")
                    .filter(existing -> existing.getAmount().equals(amount))
                    .orElseGet(() -> transactionRepository
                            .findFirstByBooking_IdAndStatusOrderByCreatedAtDesc(bookingId, "AUTHORIZED")
                            .filter(existing -> existing.getAmount().equals(amount))
                            .orElse(null));
            if (transaction != null) {
                return orderResult(transaction);
            }
            transaction = new PaymentTransaction();
            transaction.setBooking(booking);
            transaction.setCustomer(customer);
            transaction.setIdempotencyKey(idempotencyKey);
            transaction.setAmount(amount);
            transaction.setCurrency(CURRENCY);
            transaction.setStatus("ORDER_CREATING");
            transaction = transactionRepository.saveAndFlush(transaction);
        }

        long amountInPaise = amount.movePointRight(2).longValueExact();
        JsonNode gatewayOrder;
        try {
            gatewayOrder = gatewayClient.createOrder(keyId, keySecret, Map.of(
                            "amount", amountInPaise,
                            "currency", CURRENCY,
                            "receipt", booking.getBookingId(),
                            "notes", Map.of(
                                    "homefix_booking_id", booking.getBookingId(),
                                    "homefix_customer_id", customer.getId().toString()
                            )));
        } catch (RestClientException exception) {
            transaction.setStatus("ORDER_FAILED");
            transaction.setFailureReason("Payment gateway order creation failed.");
            transactionRepository.save(transaction);
            return PaymentOrderResult.failed();
        }
        if (gatewayOrder == null || !gatewayOrder.hasNonNull("id")
                || gatewayOrder.path("amount").asLong(-1) != amountInPaise
                || !CURRENCY.equals(gatewayOrder.path("currency").asText())) {
            transaction.setStatus("ORDER_FAILED");
            transaction.setFailureReason("Payment gateway returned an invalid order response.");
            transactionRepository.save(transaction);
            return PaymentOrderResult.failed();
        }
        transaction.setGatewayOrderId(gatewayOrder.path("id").asText());
        transaction.setStatus("ORDER_CREATED");
        transaction.setFailureReason(null);
        transactionRepository.save(transaction);
        payment.setStatus("PENDING");
        payment.setMethod("UPI");
        paymentRepository.save(payment);
        return orderResult(transaction);
    }

    @Transactional
    public Map<String, Object> verifyCheckoutPayment(
            Long transactionId, User customer, String orderId, String paymentId, String signature) {
        requireConfigured();
        PaymentTransaction transaction = transactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new PaymentOperationException(
                        HttpStatus.NOT_FOUND, "Payment attempt was not found."));
        requireTransactionOwner(transaction, customer);
        if (!orderId.equals(transaction.getGatewayOrderId())) {
            throw new PaymentOperationException(HttpStatus.BAD_REQUEST,
                    "The supplied gateway order does not match this payment attempt.");
        }
        if (isCapturedOrRefunded(transaction.getStatus())) {
            if (!paymentId.equals(transaction.getGatewayPaymentId())) {
                throw new PaymentOperationException(HttpStatus.CONFLICT,
                        "A different gateway payment has already been recorded.");
            }
            return verificationResult(transaction);
        }
        String expectedSignature = hmacHex(keySecret, orderId + "|" + paymentId);
        if (!constantTimeEquals(expectedSignature, signature)) {
            throw new PaymentOperationException(HttpStatus.BAD_REQUEST,
                    "The gateway payment signature is invalid.");
        }

        JsonNode gatewayPayment;
        try {
            gatewayPayment = gatewayClient.fetchPayment(keyId, keySecret, paymentId);
        } catch (RestClientException exception) {
            throw new PaymentOperationException(HttpStatus.BAD_GATEWAY,
                    "Payment verification is temporarily unavailable. Retry safely.");
        }
        validateGatewayPayment(gatewayPayment, transaction, orderId, paymentId);
        updateFromGatewayPayment(transaction, gatewayPayment);
        return verificationResult(transaction);
    }

    @Transactional
    public WebhookResult processWebhook(String rawBody, String signature, String eventId) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new PaymentOperationException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Payment webhook verification is not configured.");
        }
        if (eventId == null || eventId.isBlank() || eventId.length() > 100) {
            throw new PaymentOperationException(HttpStatus.BAD_REQUEST,
                    "A valid gateway event ID is required.");
        }
        String expectedSignature = hmacHex(webhookSecret, rawBody);
        if (!constantTimeEquals(expectedSignature, signature)) {
            throw new PaymentOperationException(HttpStatus.BAD_REQUEST,
                    "The gateway webhook signature is invalid.");
        }
        if (webhookEventRepository.existsByGatewayEventId(eventId)) {
            return new WebhookResult(true, "Duplicate gateway event ignored.");
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (JsonProcessingException exception) {
            throw new PaymentOperationException(HttpStatus.BAD_REQUEST,
                    "The gateway webhook payload is invalid.");
        }
        String eventType = payload.path("event").asText("");
        if (Set.of("refund.created", "refund.processed", "refund.failed").contains(eventType)) {
            processRefundWebhook(payload, eventType);
            webhookEventRepository.save(new PaymentWebhookEvent(eventId, eventType, sha256(rawBody)));
            return new WebhookResult(false, "Gateway refund event processed.");
        }
        if (!Set.of("payment.captured", "payment.failed").contains(eventType)) {
            webhookEventRepository.save(new PaymentWebhookEvent(
                    eventId, eventType.isBlank() ? "unknown" : eventType, sha256(rawBody)));
            return new WebhookResult(false, "Unsupported gateway event recorded without payment changes.");
        }
        JsonNode gatewayPayment = payload.path("payload").path("payment").path("entity");
        String orderId = gatewayPayment.path("order_id").asText("");
        String gatewayStatus = gatewayPayment.path("status").asText("");
        String expectedStatus = "payment.captured".equals(eventType) ? "captured" : "failed";
        String gatewayPaymentId = gatewayPayment.path("id").asText("");
        if (!expectedStatus.equalsIgnoreCase(gatewayStatus)) {
            throw new PaymentOperationException(HttpStatus.BAD_REQUEST,
                    "The gateway event type does not match its payment status.");
        }
        if (orderId.isBlank() || gatewayPaymentId.isBlank()) {
            throw new PaymentOperationException(HttpStatus.BAD_REQUEST,
                    "The gateway event does not identify its payment and order.");
        }
        PaymentTransaction transaction = transactionRepository.findByGatewayOrderIdForUpdate(orderId)
                .orElseThrow(() -> new PaymentOperationException(
                        HttpStatus.NOT_FOUND, "Payment order is not yet available for webhook processing."));
        validateGatewayPayment(gatewayPayment, transaction, orderId, gatewayPaymentId);
        updateFromGatewayPayment(transaction, gatewayPayment);
        webhookEventRepository.save(new PaymentWebhookEvent(eventId, eventType, sha256(rawBody)));
        return new WebhookResult(false, "Gateway event processed.");
    }

    private void processRefundWebhook(JsonNode payload, String eventType) {
        JsonNode gatewayRefund = payload.path("payload").path("refund").path("entity");
        String gatewayRefundId = gatewayRefund.path("id").asText("");
        String gatewayPaymentId = gatewayRefund.path("payment_id").asText("");
        if (gatewayRefundId.isBlank() || gatewayPaymentId.isBlank()) {
            throw new PaymentOperationException(HttpStatus.BAD_REQUEST,
                    "The gateway refund event does not identify its refund and payment.");
        }
        String internalRefundId = gatewayRefund.path("notes")
                .path("homefix_refund_id").asText("");
        Optional<PaymentRefund> gatewayMatch =
                refundRepository.findByGatewayRefundIdForUpdate(gatewayRefundId);
        PaymentRefund refund = gatewayMatch.orElseGet(() ->
                refundByInternalId(internalRefundId));
        if (!internalRefundId.isBlank()
                && !refund.getId().toString().equals(internalRefundId)) {
            throw new PaymentOperationException(HttpStatus.CONFLICT,
                    "The gateway refund event does not match its saved refund request.");
        }
        if (refund.getGatewayRefundId() != null
                && !gatewayRefundId.equals(refund.getGatewayRefundId())) {
            throw new PaymentOperationException(HttpStatus.CONFLICT,
                    "The saved refund is already linked to a different gateway refund.");
        }
        PaymentTransaction transaction = transactionRepository
                .findByIdForUpdate(refund.getTransaction().getId())
                .orElseThrow(() -> new PaymentOperationException(
                        HttpStatus.NOT_FOUND, "Payment transaction was not found."));
        long expectedAmount = refund.getAmount().movePointRight(2).longValueExact();
        if (!gatewayPaymentId.equals(transaction.getGatewayPaymentId())
                || gatewayRefund.path("amount").asLong(-1) != expectedAmount) {
            throw new PaymentOperationException(HttpStatus.CONFLICT,
                    "The gateway refund event does not match the saved payment and amount.");
        }
        refund.setGatewayRefundId(gatewayRefundId);
        String gatewayStatus = gatewayRefund.path("status").asText("");
        String expectedGatewayStatus = switch (eventType) {
            case "refund.processed" -> "processed";
            case "refund.failed" -> "failed";
            default -> "pending";
        };
        if (!expectedGatewayStatus.equalsIgnoreCase(gatewayStatus)) {
            throw new PaymentOperationException(HttpStatus.BAD_REQUEST,
                    "The gateway refund event does not match its refund status.");
        }
        String nextStatus = switch (eventType) {
            case "refund.processed" -> "PROCESSED";
            case "refund.failed" -> "FAILED";
            default -> "PENDING";
        };
        if ("PROCESSED".equals(refund.getStatus()) && !"PROCESSED".equals(nextStatus)) {
            throw new PaymentOperationException(HttpStatus.CONFLICT,
                    "A processed refund cannot be moved to another status.");
        }
        refund.setStatus(nextStatus);
        refund.setFailureReason("FAILED".equals(nextStatus)
                ? "Razorpay reported that the refund failed." : null);
        refundRepository.save(refund);
        synchronizeRefundStatus(transaction);
        auditLogRepository.save(new AuditLog(
                null, "PAYMENT_REFUND_" + nextStatus, "PAYMENT_REFUND",
                refund.getId().toString(),
                "Razorpay confirmed refund status " + nextStatus + "."));
    }

    private PaymentRefund refundByInternalId(String internalRefundId) {
        try {
            return refundRepository.findByIdForUpdate(Long.valueOf(internalRefundId))
                    .orElseThrow(() -> new PaymentOperationException(
                            HttpStatus.NOT_FOUND,
                            "Refund request is not yet available for reconciliation."));
        } catch (NumberFormatException exception) {
            throw new PaymentOperationException(HttpStatus.NOT_FOUND,
                    "Refund request is not yet available for reconciliation.");
        }
    }

    private void synchronizeRefundStatus(PaymentTransaction transaction) {
        BigDecimal processed = refundRepository.sumAmountsByTransactionAndStatuses(
                transaction.getId(), List.of("PROCESSED"));
        BigDecimal pending = refundRepository.sumAmountsByTransactionAndStatuses(
                transaction.getId(), List.of("PENDING"));
        if (processed.signum() > 0) {
            String status = processed.compareTo(transaction.getAmount()) >= 0
                    ? "REFUNDED" : "PARTIALLY_REFUNDED";
            transaction.setStatus(status);
            transactionRepository.save(transaction);
            paymentRepository.findByBooking_IdForUpdate(transaction.getBooking().getId())
                    .ifPresent(payment -> {
                        payment.setStatus(status);
                        payment.setAdminNote("Refunded INR " + processed.toPlainString()
                                + " through Razorpay.");
                        paymentRepository.save(payment);
                    });
        } else if (pending.signum() > 0) {
            paymentRepository.findByBooking_IdForUpdate(transaction.getBooking().getId())
                    .ifPresent(payment -> {
                        payment.setAdminNote("A Razorpay refund is awaiting gateway confirmation.");
                        paymentRepository.save(payment);
                    });
        }
    }

    public List<PaymentRefundRefundView> refundsForTransaction(Long transactionId) {
        return refundRepository.findAllByTransaction_IdOrderByCreatedAtDesc(transactionId).stream()
                .map(this::refundView)
                .toList();
    }

    private PaymentRefundRefundView refundView(PaymentRefund refund) {
        return new PaymentRefundRefundView(
                refund.getId(),
                refund.getTransaction().getId(),
                refund.getAmount(),
                refund.getReason(),
                refund.getStatus(),
                refund.getGatewayRefundId() == null ? "" : refund.getGatewayRefundId(),
                refund.getFailureReason() == null ? "" : refund.getFailureReason(),
                refund.getRequestedByAdmin().getFullName(),
                refund.getCreatedAt(),
                refund.getUpdatedAt());
    }

    private void updateFromGatewayPayment(PaymentTransaction transaction, JsonNode gatewayPayment) {
        if (isCapturedOrRefunded(transaction.getStatus())) {
            return;
        }
        String gatewayStatus = gatewayPayment.path("status").asText("");
        String paymentId = gatewayPayment.path("id").asText("");
        Payment payment = paymentRepository.findByBooking_IdForUpdate(transaction.getBooking().getId())
                .orElseThrow(() -> new PaymentOperationException(
                        HttpStatus.CONFLICT, "Payment record is not available."));

        if ("captured".equalsIgnoreCase(gatewayStatus)) {
            transaction.setGatewayPaymentId(paymentId);
            transaction.setStatus("CAPTURED");
            transaction.setFailureReason(null);
            if (!"PAID".equalsIgnoreCase(payment.getStatus())) {
                payment.setStatus("PAID");
                payment.setMethod("UPI");
                payment.setExternalReference(paymentId);
                payment.setAdminNote("Payment verified by Razorpay.");
                paymentRepository.save(payment);
                notificationRepository.save(new Notification(
                        transaction.getCustomer(), "UPI payment verified",
                        "Payment for booking " + transaction.getBooking().getBookingId()
                                + " was verified by Razorpay.",
                        "PAYMENT"));
                auditLogRepository.save(new AuditLog(
                        null, "PAYMENT_GATEWAY_CAPTURED", "PAYMENT",
                        String.valueOf(transaction.getId()),
                        "Razorpay captured payment for booking "
                                + transaction.getBooking().getBookingId()));
            }
        } else if ("failed".equalsIgnoreCase(gatewayStatus)) {
            transaction.setGatewayPaymentId(paymentId);
            transaction.setStatus("FAILED");
            transaction.setFailureReason("Razorpay reported a failed payment.");
            if (!"PAID".equalsIgnoreCase(payment.getStatus())) {
                payment.setStatus("UNPAID");
                payment.setAdminNote("The last Razorpay attempt failed; a new attempt may be created.");
                paymentRepository.save(payment);
            }
            auditLogRepository.save(new AuditLog(
                    null, "PAYMENT_GATEWAY_FAILED", "PAYMENT",
                    String.valueOf(transaction.getId()),
                    "Razorpay reported a failed payment for booking "
                            + transaction.getBooking().getBookingId()));
        } else {
            transaction.setGatewayPaymentId(paymentId);
            transaction.setStatus("AUTHORIZED");
            transaction.setFailureReason(null);
            if (!"PAID".equalsIgnoreCase(payment.getStatus())) {
                payment.setStatus("PENDING");
                payment.setAdminNote("Awaiting gateway capture confirmation.");
                paymentRepository.save(payment);
            }
        }
        transactionRepository.save(transaction);
    }

    private void validateGatewayPayment(
            JsonNode gatewayPayment,
            PaymentTransaction transaction,
            String expectedOrderId,
            String expectedPaymentId) {
        long expectedAmount = transaction.getAmount().movePointRight(2).longValueExact();
        if (gatewayPayment == null
                || !expectedOrderId.equals(gatewayPayment.path("order_id").asText())
                || gatewayPayment.path("amount").asLong(-1) != expectedAmount
                || !transaction.getCurrency().equals(gatewayPayment.path("currency").asText())
                || !expectedPaymentId.equals(gatewayPayment.path("id").asText())
                || !gatewayPayment.hasNonNull("id")) {
            throw new PaymentOperationException(HttpStatus.CONFLICT,
                    "The gateway payment details do not match the saved booking amount.");
        }
    }

    private void requireTransactionOwner(PaymentTransaction transaction, User customer) {
        if (!transaction.getCustomer().getId().equals(customer.getId())) {
            throw new PaymentOperationException(HttpStatus.NOT_FOUND,
                    "Payment attempt was not found for this account.");
        }
    }

    private boolean isCapturedOrRefunded(String status) {
        return Set.of("CAPTURED", "PARTIALLY_REFUNDED", "REFUNDED").contains(status);
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new PaymentOperationException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay checkout is not configured on this server.");
        }
    }

    private PaymentOrderResult orderResult(PaymentTransaction transaction) {
        return new PaymentOrderResult(
                true,
                transaction.getId(),
                transaction.getGatewayOrderId(),
                transaction.getAmount().movePointRight(2).longValueExact(),
                transaction.getCurrency(),
                keyId,
                transaction.getStatus(),
                "");
    }

    private Map<String, Object> verificationResult(PaymentTransaction transaction) {
        return Map.of(
                "transactionId", transaction.getId(),
                "status", transaction.getStatus(),
                "verified", isCapturedOrRefunded(transaction.getStatus()),
                "message", "Gateway status: " + transaction.getStatus().toLowerCase()
        );
    }

    private String hmacHex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Payment signature verification is unavailable.", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                provided.getBytes(StandardCharsets.US_ASCII));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Webhook event fingerprinting is unavailable.", exception);
        }
    }

    public record PaymentRefundRefundView(
            Long refundId,
            Long transactionId,
            BigDecimal amount,
            String reason,
            String status,
            String gatewayRefundId,
            String failureReason,
            String requestedBy,
            java.time.Instant createdAt,
            java.time.Instant updatedAt) {
    }

    public record PaymentOrderResult(
            boolean successful,
            Long transactionId,
            String orderId,
            Long amountInPaise,
            String currency,
            String keyId,
            String status,
            String message) {
        static PaymentOrderResult failed() {
            return new PaymentOrderResult(
                    false, null, null, null, null, null, "ORDER_FAILED",
                    "Razorpay could not create the payment order. Retry safely.");
        }
    }

    public record WebhookResult(boolean duplicate, String message) {
    }
}
