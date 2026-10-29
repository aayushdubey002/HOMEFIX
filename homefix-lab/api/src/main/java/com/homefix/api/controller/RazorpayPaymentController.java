package com.homefix.api.controller;

import com.homefix.api.dto.VerifyRazorpayPaymentRequest;
import com.homefix.api.model.User;
import com.homefix.api.repository.UserRepository;
import com.homefix.api.service.PaymentOperationException;
import com.homefix.api.service.RazorpayPaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payments/razorpay")
public class RazorpayPaymentController {

    private final RazorpayPaymentService paymentService;
    private final UserRepository userRepository;

    public RazorpayPaymentController(
            RazorpayPaymentService paymentService,
            UserRepository userRepository) {
        this.paymentService = paymentService;
        this.userRepository = userRepository;
    }

    @PostMapping("/orders/{bookingId}")
    public ResponseEntity<?> createOrder(
            @PathVariable Long bookingId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{8,80}")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "A valid idempotency key is required."));
        }
        User customer = currentCustomer(jwt);
        if (customer == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "An active customer account is required."));
        }
        try {
            RazorpayPaymentService.PaymentOrderResult result =
                    paymentService.createOrder(bookingId, customer, idempotencyKey);
            if (!result.successful()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Map.of("message", result.message()));
            }
            return ResponseEntity.ok(Map.of(
                    "transactionId", result.transactionId(),
                    "orderId", result.orderId(),
                    "amount", result.amountInPaise(),
                    "currency", result.currency(),
                    "keyId", result.keyId(),
                    "status", result.status()
            ));
        } catch (PaymentOperationException exception) {
            return failure(exception);
        }
    }

    @PostMapping("/transactions/{transactionId}/verify")
    public ResponseEntity<?> verifyPayment(
            @PathVariable Long transactionId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody VerifyRazorpayPaymentRequest request) {
        User customer = currentCustomer(jwt);
        if (customer == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "An active customer account is required."));
        }
        try {
            return ResponseEntity.ok(paymentService.verifyCheckoutPayment(
                    transactionId, customer, request.razorpayOrderId(),
                    request.razorpayPaymentId(), request.razorpaySignature()));
        } catch (PaymentOperationException exception) {
            return failure(exception);
        }
    }

    @PostMapping("/transactions/{transactionId}/cancel")
    public ResponseEntity<?> cancelCheckout(
            @PathVariable Long transactionId,
            @AuthenticationPrincipal Jwt jwt) {
        User customer = currentCustomer(jwt);
        if (customer == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "An active customer account is required."));
        }
        try {
            return ResponseEntity.ok(Map.of(
                    "status", paymentService.cancelCheckout(transactionId, customer)));
        } catch (PaymentOperationException exception) {
            return failure(exception);
        }
    }

    @GetMapping("/bookings/{bookingId}/refunds")
    public ResponseEntity<?> customerRefunds(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal Jwt jwt) {
        User customer = currentCustomer(jwt);
        if (customer == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "An active customer account is required."));
        }
        try {
            return ResponseEntity.ok(paymentService.customerRefunds(bookingId, customer));
        } catch (PaymentOperationException exception) {
            return failure(exception);
        }
    }

    @GetMapping("/bookings/{bookingId}/history")
    public ResponseEntity<?> customerPaymentHistory(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal Jwt jwt) {
        User customer = currentCustomer(jwt);
        if (customer == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "An active customer account is required."));
        }
        try {
            return ResponseEntity.ok(paymentService.customerPaymentHistory(bookingId, customer));
        } catch (PaymentOperationException exception) {
            return failure(exception);
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        try {
            RazorpayPaymentService.WebhookResult result =
                    paymentService.processWebhook(rawBody, signature, eventId);
            return ResponseEntity.ok(Map.of(
                    "duplicate", result.duplicate(),
                    "message", result.message()
            ));
        } catch (PaymentOperationException exception) {
            return failure(exception);
        }
    }

    private User currentCustomer(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        try {
            return userRepository.findById(Long.valueOf(jwt.getSubject()))
                    .filter(user -> user.isActive()
                            && "CUSTOMER".equalsIgnoreCase(user.getRole()))
                    .orElse(null);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private ResponseEntity<Map<String, String>> failure(PaymentOperationException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage()));
    }
}
