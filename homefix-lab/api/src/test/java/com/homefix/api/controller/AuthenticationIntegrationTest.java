package com.homefix.api.controller;

import com.homefix.api.model.BookingEntity;
import com.homefix.api.model.BookingPriceHistory;
import com.homefix.api.model.Invoice;
import com.homefix.api.model.User;
import com.homefix.api.repository.BookingDismissalRepository;
import com.homefix.api.repository.BookingRepository;
import com.homefix.api.repository.BookingPriceHistoryRepository;
import com.homefix.api.repository.UserRepository;
import com.homefix.api.repository.CustomerProfileRepository;
import com.homefix.api.repository.ProviderProfileRepository;
import com.homefix.api.repository.ProviderSkillRepository;
import com.homefix.api.repository.BookingStatusHistoryRepository;
import com.homefix.api.repository.InvoiceRepository;
import com.homefix.api.repository.PaymentRepository;
import com.homefix.api.repository.ReviewRepository;
import com.homefix.api.repository.SupportTicketRepository;
import com.homefix.api.repository.NotificationRepository;
import com.homefix.api.repository.AuditLogRepository;
import com.homefix.api.repository.ProviderAvailabilityRepository;
import com.homefix.api.repository.ProviderVerificationDocumentRepository;
import com.homefix.api.repository.PlatformSettingRepository;
import com.homefix.api.repository.ServiceCategoryRepository;
import com.homefix.api.repository.PaymentTransactionRepository;
import com.homefix.api.repository.PaymentRefundRepository;
import com.homefix.api.repository.PaymentWebhookEventRepository;
import com.homefix.api.model.Payment;
import com.homefix.api.model.PaymentTransaction;
import com.homefix.api.model.PaymentRefund;
import com.homefix.api.service.RazorpayGatewayClient;
import com.homefix.api.model.ProviderAvailability;
import com.homefix.api.model.ProviderProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.ResourceAccessException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.payment.razorpay.key-id=rzp_test_homefix",
        "app.payment.razorpay.key-secret=homefix-test-key-secret",
        "app.payment.razorpay.webhook-secret=homefix-test-webhook-secret"
})
class AuthenticationIntegrationTest {

    private static final String FRONTEND_ORIGIN =
"http://localhost:5173";

    private BigDecimal plumbingStartingPriceBeforeTest;

    @Value("${local.server.port}")
    private int serverPort;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingDismissalRepository dismissalRepository;

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    @Autowired
    private ProviderProfileRepository providerProfileRepository;

    @Autowired
    private ProviderAvailabilityRepository providerAvailabilityRepository;

    @Autowired
    private ProviderSkillRepository providerSkillRepository;

    @Autowired
    private ServiceCategoryRepository serviceCategoryRepository;

    @Autowired
    private BookingStatusHistoryRepository historyRepository;

    @Autowired
    private BookingPriceHistoryRepository bookingPriceHistoryRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private SupportTicketRepository ticketRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ProviderAvailabilityRepository availabilityRepository;

        @Autowired
        private ProviderVerificationDocumentRepository providerDocumentRepository;

        @Autowired
        private PlatformSettingRepository platformSettingRepository;

        @Autowired
        private PaymentTransactionRepository paymentTransactionRepository;

        @Autowired
        private PaymentRefundRepository paymentRefundRepository;

        @Autowired
        private PaymentWebhookEventRepository paymentWebhookEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RazorpayGatewayClient razorpayGatewayClient;

    @BeforeEach
    void clearDatabase() {
        plumbingStartingPriceBeforeTest = serviceCategoryRepository
                .findByServiceCodeIgnoreCase("PLUMBING")
                .map(category -> category.getStartingPrice())
                .orElse(null);
        dismissalRepository.deleteAll();
        paymentWebhookEventRepository.deleteAll();
        paymentRefundRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        providerDocumentRepository.deleteAll();
        platformSettingRepository.deleteAll();
        bookingPriceHistoryRepository.deleteAll();
        historyRepository.deleteAll();
        invoiceRepository.deleteAll();
        paymentRepository.deleteAll();
        reviewRepository.deleteAll();
        ticketRepository.deleteAll();
        notificationRepository.deleteAll();
        bookingRepository.deleteAll();
        auditLogRepository.deleteAll();
        customerProfileRepository.deleteAll();
        providerSkillRepository.deleteAll();
        availabilityRepository.deleteAll();
        providerProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void restoreServiceCategoryPrices() {
        if (plumbingStartingPriceBeforeTest == null) {
            return;
        }

        serviceCategoryRepository.findByServiceCodeIgnoreCase("PLUMBING")
                .ifPresent(category -> {
                    category.setStartingPrice(plumbingStartingPriceBeforeTest);
                    serviceCategoryRepository.save(category);
                });
    }

    @Test
    void loginPreflightAllowsLocalFrontendOrigin() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + "/api/auth/login"))
                .header("Origin", FRONTEND_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,idempotency-key")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
        assertEquals(
                FRONTEND_ORIGIN,
                response.headers().firstValue("Access-Control-Allow-Origin").orElse(null)
        );
        assertTrue(response.headers()
                .firstValue("Access-Control-Allow-Methods")
                .orElse("")
                .contains("POST"));
        assertTrue(response.headers()
                .firstValue("Access-Control-Allow-Headers")
                .orElse("")
                .toLowerCase(Locale.ROOT)
                .contains("content-type"));
        assertTrue(response.headers()
                .firstValue("Access-Control-Allow-Headers")
                .orElse("")
                .toLowerCase(Locale.ROOT)
                .contains("idempotency-key"));
    }

    @Test
    void providerCanSaveWorkingHoursForEveryDay() throws Exception {
        String providerToken = registerAndLoginProvider("Availability Provider");
        String availability = """
                [
                  {"dayOfWeek":"MONDAY","startTime":"09:00","endTime":"17:00","enabled":true},
                  {"dayOfWeek":"TUESDAY","startTime":"09:00","endTime":"17:00","enabled":true},
                  {"dayOfWeek":"WEDNESDAY","startTime":"09:00","endTime":"17:00","enabled":true},
                  {"dayOfWeek":"THURSDAY","startTime":"09:00","endTime":"17:00","enabled":true},
                  {"dayOfWeek":"FRIDAY","startTime":"09:00","endTime":"17:00","enabled":true},
                  {"dayOfWeek":"SATURDAY","startTime":"09:00","endTime":"17:00","enabled":true},
                  {"dayOfWeek":"SUNDAY","startTime":"09:00","endTime":"17:00","enabled":true}
                ]
                """;

        HttpResponse<String> response = send(
                "PUT",
                "/api/provider/availability",
                providerToken,
                availability
        );

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"count\":7"));
    }

    @Test
    void providerCanLoadProfileIncludingLazyServiceCategoryDetails() throws Exception {
        String providerToken = registerAndLoginProvider("Profile Provider");

        HttpResponse<String> response = send(
                "GET",
                "/api/provider/profile",
                providerToken,
                null
        );

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"serviceCategory\":\"PLUMBER\""),
                response.body());
        assertTrue(response.body().contains("\"category\":\"Plumber\""), response.body());
    }

    @Test
    void reviewedProviderDocumentsUnlockProfileApprovalAndMatchingRequests() throws Exception {
        String adminEmail = "document-admin-" + UUID.randomUUID() + "@example.test";
        String adminPassword = UUID.randomUUID().toString();
        User admin = new User("Document Admin", adminEmail, passwordEncoder.encode(adminPassword));
        admin.setRole("ADMIN");
        userRepository.save(admin);
        String adminToken = loginToken(adminEmail, adminPassword, "ADMIN");

        String customerEmail = "document-customer-" + UUID.randomUUID() + "@example.test";
        String customerPassword = UUID.randomUUID().toString();
        User customer = new User(
                "Document Workflow Customer", customerEmail, passwordEncoder.encode(customerPassword));
        userRepository.save(customer);
        String customerToken = loginToken(customerEmail, customerPassword, "CUSTOMER");
        HttpResponse<String> requestResponse = send(
                "POST",
                "/api/service-requests",
                customerToken,
                "{\"customerName\":\"Document Workflow Customer\","
                        + "\"service\":\"PLUMBING\",\"phone\":\"9876543210\","
                        + "\"address\":\"10 Main Street\",\"preferredDate\":\""
                        + LocalDate.now().plusDays(1) + "\"}"
        );
        assertEquals(201, requestResponse.statusCode(), requestResponse.body());

        String providerToken = registerAndLoginProvider("Document Workflow Provider");
        long providerId = Long.parseLong(
                providerToken.substring(providerToken.indexOf('.') + 1,
                        providerToken.indexOf('.', providerToken.indexOf('.') + 1))
                        .replace('-', '+').replace('_', '/')
                        .transform(payload -> new String(java.util.Base64.getDecoder().decode(payload),
                                StandardCharsets.UTF_8))
                        .replaceAll(".*\\\"sub\\\":\\\"(\\d+)\\\".*", "$1"));
        User unverifiedProvider = userRepository.findById(providerId).orElseThrow();
        unverifiedProvider.setProviderApproved(false);
        userRepository.save(unverifiedProvider);
        ProviderProfile unverifiedProfile = providerProfileRepository
                .findByUser_Id(providerId).orElseThrow();
        unverifiedProfile.setVerificationStatus("PENDING");
        unverifiedProfile.setOnline(false);
        providerProfileRepository.save(unverifiedProfile);

        HttpResponse<String> identityUpload = uploadProviderDocument(
                providerToken, "IDENTITY_PROOF", "identity.pdf");
        HttpResponse<String> addressUpload = uploadProviderDocument(
                providerToken, "SERVICE_ADDRESS_PROOF", "address.pdf");
        assertEquals(201, identityUpload.statusCode(), identityUpload.body());
        assertEquals(201, addressUpload.statusCode(), addressUpload.body());
        long identityDocumentId = responseId(identityUpload.body());
        long addressDocumentId = responseId(addressUpload.body());
        HttpResponse<String> providerDocuments = send(
                "GET", "/api/provider/verification-documents", providerToken, null);
        assertEquals(200, providerDocuments.statusCode(), providerDocuments.body());
        assertTrue(providerDocuments.body().contains("\"documentType\":\"SERVICE_ADDRESS_PROOF\""),
                providerDocuments.body());
        assertTrue(providerDocuments.body().contains("\"originalFilename\":\"address.pdf\""),
                providerDocuments.body());
        HttpResponse<String> duplicateAddressUpload = uploadProviderDocument(
                providerToken, "SERVICE_ADDRESS_PROOF", "duplicate-address.pdf");
        assertEquals(409, duplicateAddressUpload.statusCode(), duplicateAddressUpload.body());
        assertTrue(duplicateAddressUpload.body().contains("already submitted or approved"),
                duplicateAddressUpload.body());
        assertTrue(duplicateAddressUpload.body().contains("\"documentType\":\"SERVICE_ADDRESS_PROOF\""),
                duplicateAddressUpload.body());
        assertTrue(duplicateAddressUpload.body().contains("\"id\":" + addressDocumentId),
                duplicateAddressUpload.body());
        assertEquals(200, send(
                "GET", "/api/admin/providers", adminToken, null).statusCode());
        assertEquals(200, send(
                "GET", "/api/admin/provider-documents", adminToken, null).statusCode());

        assertEquals(409, send(
                "PUT", "/api/provider/profile", providerToken, "{\"online\":true}").statusCode());
        assertEquals(409, send(
                "POST", "/api/admin/providers/" + providerId + "/approval", adminToken,
                "{\"status\":\"APPROVED\",\"note\":\"\"}").statusCode());

        assertEquals(200, send(
                "PUT", "/api/admin/provider-documents/" + identityDocumentId + "/review",
                adminToken, "{\"status\":\"APPROVED\",\"note\":\"\"}").statusCode());
        assertEquals(200, send(
                "PUT", "/api/admin/provider-documents/" + addressDocumentId + "/review",
                adminToken, "{\"status\":\"APPROVED\",\"note\":\"\"}").statusCode());
        assertEquals(409, uploadProviderDocument(
                providerToken, "TRADE_CERTIFICATE", "unneeded-before-profile-approval.pdf").statusCode());
        assertEquals(200, send(
                "POST", "/api/admin/providers/" + providerId + "/approval", adminToken,
                "{\"status\":\"APPROVED\",\"note\":\"\"}").statusCode());
        assertEquals(409, uploadProviderDocument(
                providerToken, "IDENTITY_PROOF", "duplicate-identity.pdf").statusCode());
        assertEquals(409, uploadProviderDocument(
                providerToken, "TRADE_CERTIFICATE", "unneeded-trade-certificate.pdf").statusCode());
        assertEquals(200, send(
                "PUT", "/api/provider/profile", providerToken, "{\"online\":true}").statusCode());

        String bookingId = bookingRepository.findAll().get(0).getBookingId();
        HttpResponse<String> providerRequests = send(
                "GET", "/api/service-requests", providerToken, null);
        assertEquals(200, providerRequests.statusCode(), providerRequests.body());
        assertTrue(providerRequests.body().contains(bookingId), providerRequests.body());
    }

    @Test
    void configuredGatewayAllowsUpiBookingsAndAdminLedger() throws Exception {
        String adminEmail = "upi-admin-" + UUID.randomUUID() + "@example.test";
        String adminPassword = UUID.randomUUID().toString();
        User admin = new User("UPI Admin", adminEmail, passwordEncoder.encode(adminPassword));
        admin.setRole("ADMIN");
        userRepository.save(admin);
        String adminToken = loginToken(adminEmail, adminPassword, "ADMIN");

        HttpResponse<String> updateConfiguration = send(
                "PUT",
                "/api/admin/payment-config",
                adminToken,
                "{\"upiId\":\"homefix@bank\"}"
        );
        assertEquals(200, updateConfiguration.statusCode(), updateConfiguration.body());

        String customerEmail = "upi-customer-" + UUID.randomUUID() + "@example.test";
        String customerPassword = UUID.randomUUID().toString();
        userRepository.save(new User("UPI Customer", customerEmail, passwordEncoder.encode(customerPassword)));
        String customerToken = loginToken(customerEmail, customerPassword, "CUSTOMER");
        HttpResponse<String> createBooking = send(
                "POST",
                "/api/service-requests",
                customerToken,
                "{\"customerName\":\"UPI Customer\",\"service\":\"PLUMBING\","
                        + "\"phone\":\"9876543210\",\"address\":\"10 Main Street\","
                        + "\"preferredDate\":\"" + LocalDate.now().plusDays(2)
                        + "\",\"paymentMethod\":\"UPI\"}"
        );

        assertEquals(201, createBooking.statusCode(), createBooking.body());
        assertTrue(createBooking.body().contains("\"paymentMethod\":\"UPI\""));
        assertTrue(createBooking.body().contains("\"paymentStatus\":\"UNPAID\""));
        assertTrue(createBooking.body().contains("\"merchantUpiId\":\"\""));

        BookingEntity ledgerBooking = bookingRepository.findAll().get(0);
        PaymentTransaction ledgerAttempt = new PaymentTransaction();
        ledgerAttempt.setBooking(ledgerBooking);
        ledgerAttempt.setCustomer(ledgerBooking.getUser());
        ledgerAttempt.setIdempotencyKey("admin-ledger-" + UUID.randomUUID());
        ledgerAttempt.setGatewayOrderId("order_test_admin_1");
        ledgerAttempt.setAmount(new java.math.BigDecimal("300.00"));
        ledgerAttempt.setStatus("ORDER_CREATED");
        paymentTransactionRepository.save(ledgerAttempt);

        HttpResponse<String> adminPayments = send(
                "GET", "/api/admin/payments", adminToken, null);
        assertEquals(200, adminPayments.statusCode(), adminPayments.body());
        assertTrue(adminPayments.body().contains("\"method\":\"UPI\""));
        assertTrue(adminPayments.body().contains("\"status\":\"UNPAID\""));
        assertTrue(adminPayments.body().contains("\"customer\":\"UPI Customer\""));
        assertTrue(adminPayments.body().contains("\"gatewayOrderId\":\"order_test_admin_1\""));
        assertTrue(adminPayments.body().contains("\"status\":\"ORDER_CREATED\""));
        HttpResponse<String> manualStatusUpdate = send(
                "PUT",
                "/api/admin/payments/" + paymentRepository.findAll().get(0).getId(),
                adminToken,
                "{\"status\":\"PAID\",\"reference\":\"manual-ref\",\"note\":\"manual override\"}"
        );
        assertEquals(404, manualStatusUpdate.statusCode(), manualStatusUpdate.body());
        assertEquals("UNPAID", paymentRepository.findAll().get(0).getStatus());
    }

    @Test
    void customerCanDownloadOnlyTheirCompletedBookingInvoice() throws Exception {
        String customerEmail = "invoice-customer-" + UUID.randomUUID() + "@example.test";
        String customerPassword = UUID.randomUUID().toString();
        User customer = new User("Invoice Customer", customerEmail,
                passwordEncoder.encode(customerPassword));
        customer = userRepository.save(customer);
        BookingEntity booking = new BookingEntity(
                "HF" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                customer.getFullName(), "PLUMBING", "Invoice Provider", "COMPLETED", customer);
        booking.setPhone("9876543210");
        booking.setAddress("12 Main Street");
        booking.setPreferredDate(LocalDate.now().plusDays(1).toString());
        booking.setPrice(320);
        booking = bookingRepository.save(booking);

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(new java.math.BigDecimal("320.00"));
        payment.setMethod("UPI");
        payment.setStatus("PENDING");
        paymentRepository.save(payment);

        Invoice invoice = new Invoice();
        invoice.setBooking(booking);
        invoice.setInvoiceNumber("HF-" + booking.getBookingId());
        invoice.setSubtotal(new java.math.BigDecimal("320.00"));
        invoice.setTax(java.math.BigDecimal.ZERO);
        invoice.setTotal(new java.math.BigDecimal("320.00"));
        invoiceRepository.save(invoice);

        BookingPriceHistory initialEstimate = new BookingPriceHistory();
        initialEstimate.setBooking(booking);
        initialEstimate.setActor(customer);
        initialEstimate.setBaseAmount(new java.math.BigDecimal("300.00"));
        initialEstimate.setProposedTotal(new java.math.BigDecimal("300.00"));
        initialEstimate.setStatus("APPROVED");
        initialEstimate.setExplanation("Initial server-calculated estimate");
        bookingPriceHistoryRepository.save(initialEstimate);

        BookingPriceHistory approvedCharge = new BookingPriceHistory();
        approvedCharge.setBooking(booking);
        approvedCharge.setBaseAmount(new java.math.BigDecimal("300.00"));
        approvedCharge.setAdditionalAmount(new java.math.BigDecimal("20.00"));
        approvedCharge.setProposedTotal(new java.math.BigDecimal("320.00"));
        approvedCharge.setStatus("APPROVED");
        approvedCharge.setExplanation("Approved additional work");
        bookingPriceHistoryRepository.save(approvedCharge);

        String customerToken = loginToken(customerEmail, customerPassword, "CUSTOMER");
        HttpResponse<byte[]> invoiceResponse = sendBytes(
                "GET", "/api/service-requests/" + booking.getId() + "/invoice/pdf",
                customerToken, null);
        assertEquals(200, invoiceResponse.statusCode());
        assertEquals("application/pdf", invoiceResponse.headers()
                .firstValue("Content-Type").orElse(""));
        assertTrue(new String(invoiceResponse.body(), 0, 4, StandardCharsets.US_ASCII)
                .startsWith("%PDF"));

        String otherEmail = "invoice-other-" + UUID.randomUUID() + "@example.test";
        String otherPassword = UUID.randomUUID().toString();
        userRepository.save(new User("Other Customer", otherEmail,
                passwordEncoder.encode(otherPassword)));
        String otherToken = loginToken(otherEmail, otherPassword, "CUSTOMER");
        HttpResponse<String> denied = send(
                "GET", "/api/service-requests/" + booking.getId() + "/invoice/pdf",
                otherToken, null);
        assertEquals(403, denied.statusCode(), denied.body());
    }

    @Test
    void signedRazorpayWebhookUpdatesPaymentOnceAndRejectsBadSignatures() throws Exception {
        User customer = new User("Gateway Customer",
                "gateway-" + UUID.randomUUID() + "@example.test", "unused");
        customer = userRepository.save(customer);
        BookingEntity booking = new BookingEntity(
                "HF" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                customer.getFullName(), "PLUMBING", "Provider", "COMPLETED", customer);
        booking.setPrice(300);
        booking = bookingRepository.save(booking);
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(new java.math.BigDecimal("300.00"));
        payment.setMethod("UPI");
        paymentRepository.save(payment);
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setBooking(booking);
        transaction.setCustomer(customer);
        transaction.setIdempotencyKey("webhook-test-" + UUID.randomUUID());
        transaction.setGatewayOrderId("order_test_" + UUID.randomUUID().toString().replace("-", ""));
        transaction.setAmount(new java.math.BigDecimal("300.00"));
        transaction.setStatus("ORDER_CREATED");
        transaction = paymentTransactionRepository.save(transaction);
        String payload = """
                {"event":"payment.captured","payload":{"payment":{"entity":{
                  "id":"pay_test_1","order_id":"%s","amount":30000,
                  "currency":"INR","status":"captured"
                }}}}
                """.formatted(transaction.getGatewayOrderId());
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");

        HttpResponse<String> rejected = sendWebhook(payload, eventId, "not-a-signature");
        assertEquals(400, rejected.statusCode(), rejected.body());
        assertEquals("UNPAID", paymentRepository.findByBooking_Id(booking.getId())
                .orElseThrow().getStatus());

        String signature = hmac("homefix-test-webhook-secret", payload);
        HttpResponse<String> accepted = sendWebhook(payload, eventId, signature);
        assertEquals(200, accepted.statusCode(), accepted.body());
        assertEquals("PAID", paymentRepository.findByBooking_Id(booking.getId())
                .orElseThrow().getStatus());
        assertEquals("pay_test_1", paymentRepository.findByBooking_Id(booking.getId())
                .orElseThrow().getExternalReference());

        HttpResponse<String> duplicate = sendWebhook(payload, eventId, signature);
        assertEquals(200, duplicate.statusCode(), duplicate.body());
        assertTrue(duplicate.body().contains("\"duplicate\":true"));
        assertEquals(1, paymentWebhookEventRepository.count());
        assertEquals("CAPTURED", paymentTransactionRepository.findById(transaction.getId())
                .orElseThrow().getStatus());

        BookingEntity failedBooking = new BookingEntity(
                "HF" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                customer.getFullName(), "PLUMBING", "Provider", "COMPLETED", customer);
        failedBooking.setPrice(300);
        failedBooking = bookingRepository.save(failedBooking);
        Payment failedPayment = new Payment();
        failedPayment.setBooking(failedBooking);
        failedPayment.setAmount(new java.math.BigDecimal("300.00"));
        failedPayment.setMethod("UPI");
        paymentRepository.save(failedPayment);
        PaymentTransaction failedTransaction = new PaymentTransaction();
        failedTransaction.setBooking(failedBooking);
        failedTransaction.setCustomer(customer);
        failedTransaction.setIdempotencyKey("webhook-failure-" + UUID.randomUUID());
        failedTransaction.setGatewayOrderId(
                "order_test_" + UUID.randomUUID().toString().replace("-", ""));
        failedTransaction.setAmount(new java.math.BigDecimal("300.00"));
        failedTransaction.setStatus("ORDER_CREATED");
        failedTransaction = paymentTransactionRepository.save(failedTransaction);
        String failedPayload = """
                {"event":"payment.failed","payload":{"payment":{"entity":{
                  "id":"pay_failed_1","order_id":"%s","amount":30000,
                  "currency":"INR","status":"failed"
                }}}}
                """.formatted(failedTransaction.getGatewayOrderId());
        String failedEventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        String failedSignature = hmac("homefix-test-webhook-secret", failedPayload);
        HttpResponse<String> failedResponse = sendWebhook(
                failedPayload, failedEventId, failedSignature);
        assertEquals(200, failedResponse.statusCode(), failedResponse.body());
        assertEquals("UNPAID", paymentRepository.findByBooking_Id(failedBooking.getId())
                .orElseThrow().getStatus());
        assertEquals("FAILED", paymentTransactionRepository.findById(failedTransaction.getId())
                .orElseThrow().getStatus());

        HttpResponse<String> failedDuplicate = sendWebhook(
                failedPayload, failedEventId, failedSignature);
        assertEquals(200, failedDuplicate.statusCode(), failedDuplicate.body());
        assertTrue(failedDuplicate.body().contains("\"duplicate\":true"));
        assertEquals(2, paymentWebhookEventRepository.count());
    }

    @Test
    void adminRefundsAreIdempotentBoundedAndOnlyProcessedAfterGatewayConfirmation()
            throws Exception {
        String adminEmail = "refund-admin-" + UUID.randomUUID() + "@example.test";
        String adminPassword = UUID.randomUUID().toString();
        User admin = new User("Refund Admin", adminEmail, passwordEncoder.encode(adminPassword));
        admin.setRole("ADMIN");
        userRepository.save(admin);
        String adminToken = loginToken(adminEmail, adminPassword, "ADMIN");

        String customerEmail = "refund-customer-" + UUID.randomUUID() + "@example.test";
        String customerPassword = UUID.randomUUID().toString();
        User customer = new User("Refund Customer", customerEmail,
                passwordEncoder.encode(customerPassword));
        customer = userRepository.save(customer);
        String customerToken = loginToken(customerEmail, customerPassword, "CUSTOMER");
        String bookingCode = "HF" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        BookingEntity booking = new BookingEntity(
                bookingCode, customer.getFullName(), "PLUMBING", "Provider", "COMPLETED", customer);
        booking.setPrice(new java.math.BigDecimal("100.00"));
        booking = bookingRepository.save(booking);
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(new java.math.BigDecimal("100.00"));
        payment.setMethod("UPI");
        payment.setStatus("PAID");
        payment.setExternalReference("pay_refund_1");
        paymentRepository.save(payment);
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setBooking(booking);
        transaction.setCustomer(customer);
        transaction.setIdempotencyKey("refund-capture-" + UUID.randomUUID());
        transaction.setGatewayOrderId("order_refund_test_1");
        transaction.setGatewayPaymentId("pay_refund_1");
        transaction.setAmount(new java.math.BigDecimal("100.00"));
        transaction.setStatus("CAPTURED");
        transaction = paymentTransactionRepository.save(transaction);

        when(razorpayGatewayClient.createRefund(
                anyString(), anyString(), eq("pay_refund_1"), anyMap()))
                .thenReturn(objectMapper.readTree("""
                        {"id":"rfnd_test_1","payment_id":"pay_refund_1",
                         "amount":4000,"status":"pending"}
                        """));

        HttpResponse<String> denied = sendRefund(
                transaction.getId(), customerToken, "refund-customer-key-001",
                "{\"amount\":40.00,\"reason\":\"Service was not fully delivered\"}");
        assertEquals(403, denied.statusCode(), denied.body());

        String idempotencyKey = "refund-admin-key-" + UUID.randomUUID();
        String requestBody = "{\"amount\":40.00,\"reason\":\"Service was not fully delivered\"}";
        HttpResponse<String> requested = sendRefund(
                transaction.getId(), adminToken, idempotencyKey, requestBody);
        assertEquals(200, requested.statusCode(), requested.body());
        assertTrue(requested.body().contains("\"status\":\"PENDING\""));
        assertTrue(requested.body().contains("Service was not fully delivered"));

        HttpResponse<String> duplicate = sendRefund(
                transaction.getId(), adminToken, idempotencyKey, requestBody);
        assertEquals(200, duplicate.statusCode(), duplicate.body());
        verify(razorpayGatewayClient, times(1))
                .createRefund(anyString(), anyString(), eq("pay_refund_1"), anyMap());

        HttpResponse<String> overBalance = sendRefund(
                transaction.getId(), adminToken, "refund-over-" + UUID.randomUUID(),
                "{\"amount\":60.01,\"reason\":\"Exceeds refundable balance\"}");
        assertEquals(400, overBalance.statusCode(), overBalance.body());

        String webhookPayload = """
                {"event":"refund.processed","payload":{"refund":{"entity":{
                  "id":"rfnd_test_1","payment_id":"pay_refund_1",
                  "amount":4000,"status":"processed"
                }}}}
                """;
        String eventId = "evt_refund_" + UUID.randomUUID().toString().replace("-", "");
        HttpResponse<String> webhook = sendWebhook(
                webhookPayload, eventId, hmac("homefix-test-webhook-secret", webhookPayload));
        assertEquals(200, webhook.statusCode(), webhook.body());
        assertEquals("PARTIALLY_REFUNDED", paymentRepository.findByBooking_Id(booking.getId())
                .orElseThrow().getStatus());
        assertEquals("PARTIALLY_REFUNDED",
                paymentTransactionRepository.findById(transaction.getId()).orElseThrow().getStatus());
        assertEquals("PROCESSED", paymentRefundRepository.findAllByTransaction_IdOrderByCreatedAtDesc(
                transaction.getId()).get(0).getStatus());
        assertTrue(auditLogRepository.findAll().stream()
                .anyMatch(audit -> "PAYMENT_REFUND_PROCESSED".equals(audit.getAction())));

        when(razorpayGatewayClient.createRefund(
                anyString(), anyString(), eq("pay_refund_1"), anyMap()))
                .thenThrow(new ResourceAccessException("Simulated uncertain gateway response"));
        String uncertainKey = "refund-uncertain-" + UUID.randomUUID();
        HttpResponse<String> uncertain = sendRefund(
                transaction.getId(), adminToken, uncertainKey,
                "{\"amount\":20.00,\"reason\":\"Second partial refund\"}");
        assertEquals(200, uncertain.statusCode(), uncertain.body());
        assertTrue(uncertain.body().contains("\"status\":\"PENDING\""));
        long uncertainRefundId = objectMapper.readTree(uncertain.body()).path("refundId").asLong();

        String reconciliationPayload = """
                {"event":"refund.processed","payload":{"refund":{"entity":{
                  "id":"rfnd_test_reconciled","payment_id":"pay_refund_1",
                  "amount":2000,"status":"processed",
                  "notes":{"homefix_refund_id":"%d"}
                }}}}
                """.formatted(uncertainRefundId);
        String reconciliationEventId =
                "evt_refund_reconcile_" + UUID.randomUUID().toString().replace("-", "");
        HttpResponse<String> reconciled = sendWebhook(
                reconciliationPayload,
                reconciliationEventId,
                hmac("homefix-test-webhook-secret", reconciliationPayload));
        assertEquals(200, reconciled.statusCode(), reconciled.body());
        PaymentRefund reconciledRefund = paymentRefundRepository.findById(uncertainRefundId)
                .orElseThrow();
        assertEquals("PROCESSED", reconciledRefund.getStatus());
        assertEquals("rfnd_test_reconciled", reconciledRefund.getGatewayRefundId());
        assertEquals("PARTIALLY_REFUNDED",
                paymentTransactionRepository.findById(transaction.getId()).orElseThrow().getStatus());
    }

    @Test
    void customerRegistrationLoginAndBookingUseAuthenticatedDatabaseIdentity()
            throws Exception {
        String email = "customer-" + UUID.randomUUID() + "@example.test";
        String password = UUID.randomUUID().toString();
        HttpResponse<String> registerResponse = send(
                "POST",
                "/api/auth/register",
                null,
                "{\"fullName\":\"Integration Customer\",\"email\":\""
                        + email + "\",\"password\":\"" + password
                        + "\",\"role\":\"CUSTOMER\",\"phone\":\"9876543210\"}"
        );
        assertEquals(201, registerResponse.statusCode());

        User registered = userRepository.findByEmail(email).orElseThrow();
        assertEquals("9876543210", registered.getPhone());
        assertTrue(passwordEncoder.matches(password, registered.getPassword()));

        HttpResponse<String> unauthorizedResponse = send(
                "GET", "/api/service-requests", null, null
        );
        assertEquals(401, unauthorizedResponse.statusCode());

        HttpResponse<String> invalidPasswordResponse = send(
                "POST",
                "/api/auth/login",
                null,
                "{\"email\":\"" + email + "\",\"password\":\"wrong-password\","
                        + "\"role\":\"CUSTOMER\"}"
        );
        assertEquals(401, invalidPasswordResponse.statusCode());
        assertFalse(invalidPasswordResponse.body().contains("accessToken"));

        HttpResponse<String> roleMismatchResponse = send(
                "POST",
                "/api/auth/login",
                null,
                "{\"email\":\"" + email + "\",\"password\":\""
                        + password + "\",\"role\":\"PROVIDER\"}"
        );
        assertEquals(403, roleMismatchResponse.statusCode());
        assertFalse(roleMismatchResponse.body().contains("accessToken"));

        HttpResponse<String> loginResponse = send(
                "POST",
                "/api/auth/login",
                null,
                "{\"email\":\"" + email + "\",\"password\":\""
                        + password + "\",\"role\":\"CUSTOMER\"}"
        );
        assertEquals(200, loginResponse.statusCode());
        assertFalse(loginResponse.body().contains("password"));
        assertTrue(loginResponse.body().contains("\"id\":" + registered.getId()));
        assertTrue(loginResponse.body().contains("\"phone\":\"9876543210\""));
        Matcher tokenMatcher = Pattern.compile("\"accessToken\":\"([^\"]+)\"")
                .matcher(loginResponse.body());
        assertTrue(tokenMatcher.find());
        String token = tokenMatcher.group(1);

        HttpResponse<String> createResponse = send(
                "POST",
                "/api/service-requests?userId=999999",
                token,
                "{\"customerName\":\"Integration Customer\","
                        + "\"service\":\"PLUMBING\",\"phone\":\"9876543210\","
                        + "\"address\":\"10 Main Street\",\"preferredDate\":\""
                        + LocalDate.now().plusDays(1)
                        + "\",\"notes\":\"Leaking kitchen tap\"}"
        );
        assertEquals(201, createResponse.statusCode());
        assertTrue(createResponse.body().contains("\"price\":300"));
        assertFalse(createResponse.body().contains("password"));
        Matcher pinMatcher = Pattern.compile("\\\"completionPin\\\":\\\"(\\d{6})\\\"")
                .matcher(createResponse.body());
        assertTrue(pinMatcher.find(), createResponse.body());
        String completionPin = pinMatcher.group(1);

        BookingEntity savedBooking = bookingRepository.findAll().get(0);
        assertEquals(registered.getId(), savedBooking.getUser().getId());
        assertEquals(new java.math.BigDecimal("300.00"), savedBooking.getPrice());
        assertEquals("PENDING", savedBooking.getStatus());

        HttpResponse<String> listResponse = send(
                "GET", "/api/service-requests?userId=999999", token, null
        );
        assertEquals(200, listResponse.statusCode());
        assertTrue(listResponse.body().contains(savedBooking.getBookingId()));
        assertTrue(listResponse.body().contains(completionPin));

        String firstProviderToken = registerAndLoginProvider("First Plumber");
        HttpResponse<String> providerRequests = send(
                "GET", "/api/service-requests?providerId=999999", firstProviderToken, null
        );
        assertEquals(200, providerRequests.statusCode());
        assertTrue(providerRequests.body().contains(savedBooking.getBookingId()));
        assertFalse(providerRequests.body().contains(completionPin));

        HttpResponse<String> acceptResponse = send(
                "PUT",
                "/api/service-requests/" + savedBooking.getId() + "/accept",
                firstProviderToken,
                null
        );
        assertEquals(200, acceptResponse.statusCode(), acceptResponse.body());
        assertTrue(acceptResponse.body().contains("\"status\":\"ACCEPTED\""));
        BookingEntity acceptedBooking = bookingRepository.findById(savedBooking.getId())
                .orElseThrow();
        assertEquals("ACCEPTED", acceptedBooking.getStatus());
        assertEquals("First Plumber", acceptedBooking.getProvider().getFullName());

        String secondProviderToken = registerAndLoginProvider("Second Plumber");
        HttpResponse<String> secondAcceptResponse = send(
                "PUT",
                "/api/service-requests/" + savedBooking.getId() + "/accept",
                secondProviderToken,
                null
        );
        assertEquals(409, secondAcceptResponse.statusCode());

        HttpResponse<String> refreshedCustomerBookings = send(
                "GET", "/api/service-requests", token, null
        );
        assertTrue(refreshedCustomerBookings.body().contains("\"status\":\"ACCEPTED\""));
        assertTrue(refreshedCustomerBookings.body().contains("\"providerName\":\"First Plumber\""));

        HttpResponse<String> forbiddenResponse = send(
                "PUT",
                "/api/service-requests/" + savedBooking.getId() + "/accept",
                token,
                null
        );
        assertEquals(403, forbiddenResponse.statusCode());
    }

    @Test
    void providerRegistrationUsesActiveServiceCatalogCategories() throws Exception {
        String email = "painter-" + UUID.randomUUID() + "@example.test";
        String password = UUID.randomUUID().toString();

        HttpResponse<String> registration = send(
                "POST",
                "/api/auth/register",
                null,
                "{\"fullName\":\"Integration Painter\",\"email\":\"" + email
                        + "\",\"password\":\"" + password
                        + "\",\"role\":\"PROVIDER\",\"phone\":\"9876543210\","
                        + "\"serviceCategory\":\"PAINTING\","
                        + "\"experience\":\"3-5_YEARS\","
                        + "\"serviceArea\":\"Central District\"}"
        );

        assertEquals(201, registration.statusCode(), registration.body());
        User provider = userRepository.findByEmail(email).orElseThrow();
        assertTrue(passwordEncoder.matches(password, provider.getPassword()));
        var profile = providerProfileRepository.findByUser_Id(provider.getId())
                .orElseThrow();
        var skills = providerSkillRepository.findByProvider_Id(profile.getId());
        assertEquals(1, skills.size());
        var painting = serviceCategoryRepository.findByServiceCodeIgnoreCase("PAINTING")
                .orElseThrow();
        assertEquals(painting.getId(), skills.get(0).getCategory().getId());
    }

    @Test
    void adminStartingPriceUpdatesExistingProviderSkillPrices() throws Exception {
        String providerEmail = "pricing-provider-" + UUID.randomUUID() + "@example.test";
        String providerPassword = UUID.randomUUID().toString();
        HttpResponse<String> registration = send(
                "POST",
                "/api/auth/register",
                null,
                "{\"fullName\":\"Pricing Provider\",\"email\":\"" + providerEmail
                        + "\",\"password\":\"" + providerPassword
                        + "\",\"role\":\"PROVIDER\",\"phone\":\"9876543210\","
                        + "\"serviceCategory\":\"PLUMBING\","
                        + "\"experience\":\"1-3_YEARS\","
                        + "\"serviceArea\":\"Central District\"}"
        );
        assertEquals(201, registration.statusCode(), registration.body());
        User provider = userRepository.findByEmail(providerEmail).orElseThrow();
        ProviderProfile profile = providerProfileRepository.findByUser_Id(provider.getId())
                .orElseThrow();
        assertEquals(1, providerSkillRepository.findByProvider_Id(profile.getId()).size());

        String adminEmail = "pricing-admin-" + UUID.randomUUID() + "@example.test";
        String adminPassword = UUID.randomUUID().toString();
        User admin = new User(
                "Pricing Admin", adminEmail, passwordEncoder.encode(adminPassword));
        admin.setRole("ADMIN");
        userRepository.save(admin);
        String adminToken = loginToken(adminEmail, adminPassword, "ADMIN");

        var category = serviceCategoryRepository.findByServiceCodeIgnoreCase("PLUMBING")
                .orElseThrow();
        String updateBody = objectMapper.writeValueAsString(java.util.Map.of(
                "name", category.getName(),
                "slug", category.getSlug(),
                "serviceCode", category.getServiceCode(),
                "description", category.getDescription(),
                "imageKey", "plumbing",
                "startingPrice", 1,
                "active", true
        ));
        HttpResponse<String> update = send(
                "PUT", "/api/admin/categories/" + category.getId(), adminToken, updateBody);

        assertEquals(200, update.statusCode(), update.body());
        assertEquals(new java.math.BigDecimal("1.00"),
                providerSkillRepository.findByProvider_Id(profile.getId()).get(0).getPrice());
    }

    @Test
    void adminEndpointsRejectCustomersAndProviders() throws Exception {
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.test";
        String adminPassword = UUID.randomUUID().toString();
        User admin = new User(
                "Integration Admin",
                adminEmail,
                passwordEncoder.encode(adminPassword)
        );
        admin.setRole("ADMIN");
        userRepository.save(admin);

        String customerEmail = "customer-" + UUID.randomUUID() + "@example.test";
        String customerPassword = UUID.randomUUID().toString();
        User customer = new User(
                "Integration Customer",
                customerEmail,
                passwordEncoder.encode(customerPassword)
        );
        userRepository.save(customer);

        String adminToken = loginToken(adminEmail, adminPassword, "ADMIN");
        String customerToken = loginToken(customerEmail, customerPassword, "CUSTOMER");
        String providerToken = registerAndLoginProvider("Permission Test Provider");

        assertEquals(200, send("GET", "/api/admin/dashboard", adminToken, null).statusCode());
        assertEquals(403, send("GET", "/api/admin/dashboard", customerToken, null).statusCode());
        assertEquals(403, send("GET", "/api/admin/dashboard", providerToken, null).statusCode());
    }

    @Test
    void adminCanUploadReplaceAndPubliclyLoadServiceCategoryImages() throws Exception {
        String adminEmail = "image-admin-" + UUID.randomUUID() + "@example.test";
        String adminPassword = UUID.randomUUID().toString();
        User admin = new User(
                "Image Admin",
                adminEmail,
                passwordEncoder.encode(adminPassword)
        );
        admin.setRole("ADMIN");
        userRepository.save(admin);
        String adminToken = loginToken(adminEmail, adminPassword, "ADMIN");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String slug = "image-category-" + suffix;
        String serviceCode = "IMAGE_" + suffix.toUpperCase(Locale.ROOT);

        HttpResponse<String> created = sendMultipart(
                "POST",
                "/api/admin/categories",
                adminToken,
                categoryJson("Uploaded service", slug, serviceCode, ""),
                pngImage(0xFF112233)
        );
        assertEquals(201, created.statusCode(), created.body());
        JsonNode createdCategory = objectMapper.readTree(created.body());
        String oldImageUrl = createdCategory.path("imageUrl").asText();
        assertTrue(oldImageUrl.matches(
                "/api/services/catalog/images/[0-9a-f-]{36}\\.png"));
        assertEquals(200, send("GET", oldImageUrl, null, null).statusCode());

        HttpResponse<String> updated = sendMultipart(
                "PUT",
                "/api/admin/categories/" + createdCategory.path("id").asLong(),
                adminToken,
                categoryJson("Uploaded service", slug, serviceCode, oldImageUrl),
                pngImage(0xFF445566)
        );
        assertEquals(200, updated.statusCode(), updated.body());
        String replacementUrl = objectMapper.readTree(updated.body())
                .path("imageUrl").asText();
        assertTrue(!oldImageUrl.equals(replacementUrl));
        HttpResponse<String> removedImage = send("GET", oldImageUrl, null, null);
        assertEquals(404, removedImage.statusCode(), removedImage.body());
        assertEquals(200, send("GET", replacementUrl, null, null).statusCode());
    }

    private String categoryJson(String name, String slug, String serviceCode, String imageUrl)
            throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "name", name,
                "slug", slug,
                "serviceCode", serviceCode,
                "description", "Image upload integration test.",
                "imageKey", "appliance-repair",
                "imageUrl", imageUrl,
                "startingPrice", 920,
                "active", true
        ));
    }

    private byte[] pngImage(int color) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) image.setRGB(x, y, color);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private String registerAndLoginProvider(String name) throws Exception {
        String email = "provider-" + UUID.randomUUID() + "@example.test";
        String password = UUID.randomUUID().toString();
        HttpResponse<String> registration = send(
                "POST",
                "/api/auth/register",
                null,
                "{\"fullName\":\"" + name + "\",\"email\":\"" + email
                        + "\",\"password\":\"" + password
                        + "\",\"role\":\"PROVIDER\",\"phone\":\"9876543210\","
                        + "\"serviceCategory\":\"PLUMBER\","
                        + "\"experience\":\"1-3_YEARS\","
                        + "\"serviceArea\":\"North District\"}"
        );
        assertEquals(201, registration.statusCode());
        User provider = userRepository.findByEmail(email).orElseThrow();
        provider.setProviderApproved(true);
        userRepository.save(provider);
        ProviderProfile profile = providerProfileRepository.findByUser_Id(provider.getId())
                .orElseThrow();
        profile.setVerificationStatus("APPROVED");
        profile.setOnline(true);
        providerProfileRepository.save(profile);
        for (java.time.DayOfWeek day : java.time.DayOfWeek.values()) {
            ProviderAvailability slot = new ProviderAvailability();
            slot.setProvider(profile);
            slot.setDayOfWeek(day);
            slot.setStartTime(java.time.LocalTime.MIN);
            slot.setEndTime(java.time.LocalTime.of(23, 59));
            slot.setEnabled(true);
            providerAvailabilityRepository.save(slot);
        }
        HttpResponse<String> login = send(
                "POST",
                "/api/auth/login",
                null,
                "{\"email\":\"" + email + "\",\"password\":\""
                        + password + "\",\"role\":\"PROVIDER\"}"
        );
        assertEquals(200, login.statusCode());
        Matcher tokenMatcher = Pattern.compile("\"accessToken\":\"([^\"]+)\"")
                .matcher(login.body());
        assertTrue(tokenMatcher.find());
        return tokenMatcher.group(1);
    }

    private String loginToken(String email, String password, String role) throws Exception {
        HttpResponse<String> login = send(
                "POST",
                "/api/auth/login",
                null,
                "{\"email\":\"" + email + "\",\"password\":\""
                        + password + "\",\"role\":\"" + role + "\"}"
        );
        assertEquals(200, login.statusCode());
        Matcher tokenMatcher = Pattern.compile("\"accessToken\":\"([^\"]+)\"")
                .matcher(login.body());
        assertTrue(tokenMatcher.find());
        return tokenMatcher.group(1);
    }

    private HttpResponse<String> send(
            String method,
            String path,
            String token,
            String body
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + path))
                .header("Accept", "application/json");
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        return HttpClient.newHttpClient().send(
                builder.method(method, publisher).build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> sendMultipart(
            String method,
            String path,
            String token,
            String categoryJson,
            byte[] image
    ) throws Exception {
        String boundary = "HomeFixBoundary" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"category\"; filename=\"category.json\"\r\n"
                + "Content-Type: application/json\r\n\r\n"
                + categoryJson + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"image\"; filename=\"service.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(image);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + path))
                .header("Accept", "application/json")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + token)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        return HttpClient.newHttpClient().send(
                request.build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<byte[]> sendBytes(
            String method,
            String path,
            String token,
            String body
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + path))
                .header("Accept", "application/pdf");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        return HttpClient.newHttpClient().send(
                builder.method(method, publisher).build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    private HttpResponse<String> sendWebhook(String body, String eventId, String signature)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + "/api/payments/razorpay/webhook"))
                .header("Content-Type", "application/json")
                .header("X-Razorpay-Event-Id", eventId)
                .header("X-Razorpay-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendRefund(
            Long transactionId, String token, String idempotencyKey, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort
                        + "/api/admin/payments/transactions/" + transactionId + "/refunds"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString());
    }

    private String hmac(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private HttpResponse<String> uploadProviderDocument(
            String token, String documentType, String filename) throws Exception {
        String boundary = "HomeFixBoundary" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"documentType\"\r\n\r\n"
                + documentType + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\""
                + filename + "\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write("%PDF-1.4\nHomeFix verification fixture\n".getBytes(StandardCharsets.UTF_8));
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort
                        + "/api/provider/verification-documents"))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private long responseId(String body) {
        Matcher matcher = Pattern.compile("\\\"id\\\":(\\d+)").matcher(body);
        assertTrue(matcher.find(), body);
        return Long.parseLong(matcher.group(1));
    }

}
