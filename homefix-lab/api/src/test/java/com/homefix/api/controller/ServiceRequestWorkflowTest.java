package com.homefix.api.controller;

import com.homefix.api.dto.BookingStatusUpdateRequest;
import com.homefix.api.dto.BookingMessageRequest;
import com.homefix.api.dto.PriceProposalDecisionRequest;
import com.homefix.api.dto.PriceProposalRequest;
import com.homefix.api.dto.ServiceRequest;
import com.homefix.api.model.BookingEntity;
import com.homefix.api.model.BookingPriceHistory;
import com.homefix.api.model.PlatformSetting;
import com.homefix.api.model.ProviderAvailability;
import com.homefix.api.model.ProviderProfile;
import com.homefix.api.model.ProviderSkill;
import com.homefix.api.model.Payment;
import com.homefix.api.model.ServiceCategory;
import com.homefix.api.model.User;
import com.homefix.api.repository.AddressRepository;
import com.homefix.api.repository.AuditLogRepository;
import com.homefix.api.repository.BookingDismissalRepository;
import com.homefix.api.repository.BookingRepository;
import com.homefix.api.repository.BookingMessageRepository;
import com.homefix.api.repository.BookingPriceHistoryRepository;
import com.homefix.api.repository.BookingStatusHistoryRepository;
import com.homefix.api.repository.CustomerProfileRepository;
import com.homefix.api.repository.InvoiceRepository;
import com.homefix.api.repository.NotificationRepository;
import com.homefix.api.repository.PaymentRepository;
import com.homefix.api.repository.PlatformSettingRepository;
import com.homefix.api.repository.ProviderAvailabilityRepository;
import com.homefix.api.repository.ProviderBookingPhotoViewRepository;
import com.homefix.api.repository.ProviderProfileRepository;
import com.homefix.api.repository.ProviderSkillRepository;
import com.homefix.api.repository.ProviderVerificationDocumentRepository;
import com.homefix.api.repository.ReviewRepository;
import com.homefix.api.repository.SupportTicketRepository;
import com.homefix.api.repository.UserRepository;
import com.homefix.api.repository.ServiceCategoryRepository;
import com.homefix.api.service.BookingPinService;
import com.homefix.api.service.BookingPricingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ServiceRequestWorkflowTest {

    @Autowired
    private ServiceRequestController controller;

    @Autowired
    private BookingMessageController bookingMessageController;

    @Autowired
    private BookingMessageRepository bookingMessageRepository;

    @Autowired
    private ProviderBookingPhotoViewRepository photoViewRepository;

        @Autowired
        private BookingPinService bookingPinService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingPriceHistoryRepository priceHistoryRepository;

    @Autowired
    private BookingPricingService bookingPricingService;

    @Autowired
    private PlatformSettingRepository platformSettingRepository;

    private String previousAdditionalChargeLimit;

    @Autowired
    private BookingDismissalRepository dismissalRepository;

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    @Autowired
    private ProviderProfileRepository providerProfileRepository;

    @Autowired
    private ProviderSkillRepository providerSkillRepository;

        @Autowired
        private ProviderVerificationDocumentRepository providerDocumentRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private BookingStatusHistoryRepository historyRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

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
    private UserRepository userRepository;

    @Autowired
    private ServiceCategoryRepository categoryRepository;

    @BeforeEach
    void clearDatabase() {
        previousAdditionalChargeLimit = platformSettingRepository
                .findBySettingKey("pricing.provider.maximum-additional-charge")
                .map(PlatformSetting::getSettingValue)
                .orElse(null);
        dismissalRepository.deleteAll();
        photoViewRepository.deleteAll();
        bookingMessageRepository.deleteAll();
        priceHistoryRepository.deleteAll();
        historyRepository.deleteAll();
        invoiceRepository.deleteAll();
        paymentRepository.deleteAll();
        reviewRepository.deleteAll();
        ticketRepository.deleteAll();
        notificationRepository.deleteAll();
        bookingRepository.deleteAll();
        auditLogRepository.deleteAll();
        addressRepository.deleteAll();
        customerProfileRepository.deleteAll();
        providerSkillRepository.deleteAll();
        providerDocumentRepository.deleteAll();
        availabilityRepository.deleteAll();
        providerProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void restoreAdditionalChargeLimit() {
        platformSettingRepository.findBySettingKey("pricing.provider.maximum-additional-charge")
                .ifPresent(setting -> {
                    if (previousAdditionalChargeLimit == null) {
                        platformSettingRepository.delete(setting);
                    } else {
                        setting.setSettingValue(previousAdditionalChargeLimit);
                        platformSettingRepository.save(setting);
                    }
                });
    }

    @Test
    void bookingChatIsPersistentAndRestrictedToCustomerAndAssignedProvider() {
        User customer = saveUser("Chat Customer", "chat-customer@example.com", "CUSTOMER", null);
        User assignedProvider = saveUser(
                "Chat Provider", "chat-provider@example.com", "PROVIDER", "PLUMBING");
        assignedProvider.setPhone("9876543210");
        assignedProvider = userRepository.save(assignedProvider);
        User otherCustomer = saveUser(
                "Other Customer", "chat-other-customer@example.com", "CUSTOMER", null);
        User otherProvider = saveUser(
                "Other Provider", "chat-other-provider@example.com", "PROVIDER", "PLUMBING");
        BookingEntity booking = new BookingEntity(
                "HF_CHAT_1", "Chat Customer", "PLUMBING",
                assignedProvider.getFullName(), "ACCEPTED", customer);
        booking.setProvider(assignedProvider);
        bookingRepository.save(booking);

        ResponseEntity<?> customerSend = bookingMessageController.sendMessage(
                booking.getId(),
                testJwt(customer, "CUSTOMER"),
                new BookingMessageRequest("Hello, I am available at 10 AM."));
        assertEquals(HttpStatus.CREATED, customerSend.getStatusCode());

        ResponseEntity<?> providerSend = bookingMessageController.sendMessage(
                booking.getId(),
                testJwt(assignedProvider, "PROVIDER"),
                new BookingMessageRequest("Thanks, I will arrive at 10 AM."));
        assertEquals(HttpStatus.CREATED, providerSend.getStatusCode());

        ResponseEntity<?> customerMessages = bookingMessageController.getMessages(
                booking.getId(), testJwt(customer, "CUSTOMER"));
        List<?> messages = assertInstanceOf(List.class, customerMessages.getBody());
        assertEquals(2, messages.size());
        assertEquals(2, bookingMessageRepository.count());

        assertEquals(HttpStatus.NOT_FOUND, bookingMessageController.getMessages(
                booking.getId(), testJwt(otherCustomer, "CUSTOMER")).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, bookingMessageController.getMessages(
                booking.getId(), testJwt(otherProvider, "PROVIDER")).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, bookingMessageController.sendMessage(
                booking.getId(),
                testJwt(otherCustomer, "CUSTOMER"),
                new BookingMessageRequest("Unauthorized message")).getStatusCode());

        booking.setStatus("IN PROGRESS");
        bookingRepository.save(booking);
        ResponseEntity<?> spacedStatusSend = bookingMessageController.sendMessage(
                booking.getId(),
                testJwt(customer, "CUSTOMER"),
                new BookingMessageRequest("I am ready for the appointment."));
        assertEquals(HttpStatus.CREATED, spacedStatusSend.getStatusCode());
        assertEquals(3, bookingMessageRepository.count());

        booking.setStatus("COMPLETED");
        bookingRepository.save(booking);
        assertEquals(HttpStatus.NOT_FOUND, bookingMessageController.getMessages(
                booking.getId(), testJwt(customer, "CUSTOMER")).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, bookingMessageController.sendMessage(
                booking.getId(),
                testJwt(assignedProvider, "PROVIDER"),
                new BookingMessageRequest("This job is complete.")).getStatusCode());
        assertEquals(3, bookingMessageRepository.count());

        booking.setStatus("ACCEPTED");
        bookingRepository.save(booking);
        ResponseEntity<?> customerBookings = controller.getServiceRequests(
                testJwt(customer, "CUSTOMER"));
        List<?> customerBookingList = assertInstanceOf(List.class, customerBookings.getBody());
        BookingEntity customerView = assertInstanceOf(BookingEntity.class, customerBookingList.get(0));
        assertEquals("9876543210", customerView.getProviderPhone());
    }

    @Test
    void providerExtraChargeChangesBookingOnlyAfterCustomerApproval() {
        PlatformSetting chargeLimit = platformSettingRepository
                .findBySettingKey("pricing.provider.maximum-additional-charge")
                .orElseGet(() -> new PlatformSetting(
                        "pricing.provider.maximum-additional-charge", "10000.00"));
        chargeLimit.setSettingValue("10000.00");
        platformSettingRepository.save(chargeLimit);

        User customer = saveUser("Customer", "price-customer@example.com", "CUSTOMER", null);
        User provider = saveUser("Assigned Plumber", "price-provider@example.com", "PROVIDER", "PLUMBING");
        provider.setExperience("1-3_YEARS");
        provider = userRepository.save(provider);
        ProviderProfile profile = providerProfileRepository.findByUser_Id(provider.getId()).orElseThrow();
        ServiceCategory category = categoryRepository.findByServiceCodeIgnoreCase("PLUMBING")
                .orElseGet(() -> categoryRepository.save(new ServiceCategory(
                        "Plumbing", "test-plumbing", "PLUMBING", "Test service", new BigDecimal("300"))));
        providerSkillRepository.save(new ProviderSkill(profile, category, new BigDecimal("450")));

        BookingEntity booking = new BookingEntity(
                "HF" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "Customer", "PLUMBING",
                provider.getFullName(), "ACCEPTED", customer);
        booking.setProvider(provider);
        booking.setPreferredAt(java.time.LocalDateTime.now().plusDays(2));
        booking.setPrice(300);
        booking = bookingRepository.save(booking);
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(new BigDecimal("300"));
        paymentRepository.save(payment);

        ResponseEntity<?> proposalResponse = controller.proposePrice(
                booking.getId(),
                testJwt(provider, "PROVIDER"),
                new PriceProposalRequest(new BigDecimal("150.00"), "Additional materials required."));

        assertEquals(HttpStatus.CREATED, proposalResponse.getStatusCode());
        BookingPriceHistory proposal = priceHistoryRepository
                .findFirstByBooking_IdAndStatusOrderByCreatedAtDesc(booking.getId(), "PENDING_APPROVAL")
                .orElseThrow();
        assertEquals(new BigDecimal("450.00"), proposal.getProposedTotal());
        assertEquals(new BigDecimal("150.00"), proposal.getAdditionalAmount());
        assertEquals(new java.math.BigDecimal("300.00"),
                bookingRepository.findById(booking.getId()).orElseThrow().getPrice());
        assertEquals(new BigDecimal("300.00"),
                paymentRepository.findByBooking_Id(booking.getId()).orElseThrow().getAmount());
        assertEquals(HttpStatus.CONFLICT, controller.proposePrice(
                booking.getId(), testJwt(provider, "PROVIDER"),
                new PriceProposalRequest(BigDecimal.ZERO, "Duplicate proposal.")).getStatusCode());

        ResponseEntity<?> approval = controller.decidePriceProposal(
                booking.getId(), proposal.getId(), testJwt(customer, "CUSTOMER"),
                new PriceProposalDecisionRequest("APPROVED"));
        assertEquals(HttpStatus.OK, approval.getStatusCode());
        assertEquals(new java.math.BigDecimal("450.00"),
                bookingRepository.findById(booking.getId()).orElseThrow().getPrice());
        assertEquals(new BigDecimal("450.00"), paymentRepository.findByBooking_Id(booking.getId())
                .orElseThrow().getAmount());
        assertEquals("APPROVED", priceHistoryRepository.findById(proposal.getId()).orElseThrow().getStatus());

        assertEquals(HttpStatus.CREATED, controller.proposePrice(
                booking.getId(), testJwt(provider, "PROVIDER"),
                new PriceProposalRequest(BigDecimal.ZERO, "Revised work scope.")).getStatusCode());
        BookingPriceHistory rejectedProposal = priceHistoryRepository
                .findFirstByBooking_IdAndStatusOrderByCreatedAtDesc(booking.getId(), "PENDING_APPROVAL")
                .orElseThrow();
        assertEquals(HttpStatus.OK, controller.decidePriceProposal(
                booking.getId(), rejectedProposal.getId(), testJwt(customer, "CUSTOMER"),
                new PriceProposalDecisionRequest("REJECTED")).getStatusCode());
        assertEquals(new java.math.BigDecimal("450.00"),
                bookingRepository.findById(booking.getId()).orElseThrow().getPrice());
        assertEquals("REJECTED",
                priceHistoryRepository.findById(rejectedProposal.getId()).orElseThrow().getStatus());
    }

    @Test
    void extraChargeUsesCurrentBookingPriceInsteadOfStaleProviderSkillPrice() {
        User customer = saveUser("AC Customer", "ac-extra-customer@example.test", "CUSTOMER", null);
        User provider = saveUser("AC Provider", "ac-extra-provider@example.test", "PROVIDER", "AC_REPAIR");
        ProviderProfile profile = providerProfileRepository.findByUser_Id(provider.getId()).orElseThrow();
        ServiceCategory category = categoryRepository.findByServiceCodeIgnoreCase("AC_REPAIR")
                .orElseGet(() -> categoryRepository.save(new ServiceCategory(
                        "AC Repair", "test-ac-repair", "AC_REPAIR",
                        "Air conditioning repair", new BigDecimal("1000.00"))));
        category.setStartingPrice(new BigDecimal("1000.00"));
        categoryRepository.save(category);
        providerSkillRepository.save(new ProviderSkill(profile, category, new BigDecimal("700.00")));

        BookingEntity booking = new BookingEntity(
                "HF_AC_EXTRA_" + java.util.UUID.randomUUID().toString().substring(0, 8),
                customer.getFullName(), "AC_REPAIR", provider.getFullName(), "IN_PROGRESS", customer);
        booking.setProvider(provider);
        booking.setPreferredAt(java.time.LocalDateTime.now().plusDays(2));
        booking.setPrice(new BigDecimal("1000.00"));
        booking = bookingRepository.save(booking);
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(new BigDecimal("1000.00"));
        payment.setMethod("CASH");
        paymentRepository.save(payment);

        PlatformSetting chargeLimit = platformSettingRepository
                .findBySettingKey("pricing.provider.maximum-additional-charge")
                .orElseGet(() -> new PlatformSetting(
                        "pricing.provider.maximum-additional-charge", "10000.00"));
        chargeLimit.setSettingValue("10000.00");
        platformSettingRepository.save(chargeLimit);
        bookingPricingService.recordInitialEstimate(
                booking, customer, bookingPricingService.initialEstimate(
                        new BigDecimal("1000.00"), booking.getPreferredAt(), "AC_REPAIR"));

        ResponseEntity<?> proposalResponse = controller.proposePrice(
                booking.getId(),
                testJwt(provider, "PROVIDER"),
                new PriceProposalRequest(new BigDecimal("2000.00"), "Replacement AC tools and materials."));

        assertEquals(HttpStatus.CREATED, proposalResponse.getStatusCode());
        BookingPriceHistory proposal = priceHistoryRepository
                .findFirstByBooking_IdAndStatusOrderByCreatedAtDesc(booking.getId(), "PENDING_APPROVAL")
                .orElseThrow();
        proposal.setServiceSubtotal(new BigDecimal("3000.00"));
        proposal.setDiscountAmount(new BigDecimal("0.05"));
        proposal.setTaxableAmount(new BigDecimal("2999.95"));
        proposal.setProposedTotal(new BigDecimal("2999.95"));
        priceHistoryRepository.save(proposal);

        ResponseEntity<?> customerBookings = controller.getServiceRequests(
                testJwt(customer, "CUSTOMER"));
        List<?> bookingViews = assertInstanceOf(List.class, customerBookings.getBody());
        BookingEntity customerView = assertInstanceOf(BookingEntity.class, bookingViews.get(0));
        assertEquals(new BigDecimal("3000.00"), customerView.getProposedPrice());
        BookingPriceHistory refreshedProposal =
                priceHistoryRepository.findById(proposal.getId()).orElseThrow();
        assertEquals(new BigDecimal("3000.00"), refreshedProposal.getProposedTotal());
        assertEquals(new BigDecimal("1000.00"), proposal.getBaseAmount());
        assertEquals(new BigDecimal("2000.00"), proposal.getAdditionalAmount());
        assertEquals(new BigDecimal("3000.00"), refreshedProposal.getProposedTotal());
        assertEquals(new BigDecimal("1000.00"),
                bookingRepository.findById(booking.getId()).orElseThrow().getPrice());

        ResponseEntity<?> approval = controller.decidePriceProposal(
                booking.getId(), proposal.getId(), testJwt(customer, "CUSTOMER"),
                new PriceProposalDecisionRequest("APPROVED"));
        assertEquals(HttpStatus.OK, approval.getStatusCode());
        assertEquals(new BigDecimal("3000.00"),
                bookingRepository.findById(booking.getId()).orElseThrow().getPrice());
        assertEquals(new BigDecimal("3000.00"),
                paymentRepository.findByBooking_Id(booking.getId()).orElseThrow().getAmount());
    }

    @Test
    void onlyBookingCustomerCanDecideAProviderPriceProposal() {
        User customer = saveUser("Customer", "price-owner@example.com", "CUSTOMER", null);
        User otherCustomer = saveUser("Other Customer", "price-other@example.com", "CUSTOMER", null);
        User provider = saveUser("Assigned Plumber", "price-assigned@example.com", "PROVIDER", "PLUMBING");
        User otherProvider = saveUser("Other Plumber", "price-other-provider@example.com", "PROVIDER", "PLUMBING");
        provider.setExperience("5_PLUS_YEARS");
        provider = userRepository.save(provider);
        ProviderProfile profile = providerProfileRepository.findByUser_Id(provider.getId()).orElseThrow();
        ServiceCategory category = categoryRepository.findByServiceCodeIgnoreCase("PLUMBING")
                .orElseGet(() -> categoryRepository.save(new ServiceCategory(
                        "Plumbing", "test-plumbing", "PLUMBING", "Test service", new BigDecimal("300"))));
        providerSkillRepository.save(new ProviderSkill(profile, category, new BigDecimal("450")));
        BookingEntity booking = new BookingEntity(
                "HF" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "Customer", "PLUMBING",
                provider.getFullName(), "ACCEPTED", customer);
        booking.setProvider(provider);
        booking.setPrice(300);
        booking.setPreferredAt(java.time.LocalDateTime.now().plusDays(2));
        booking = bookingRepository.save(booking);
        assertEquals(HttpStatus.NOT_FOUND, controller.proposePrice(
                booking.getId(), testJwt(otherProvider, "PROVIDER"),
                new PriceProposalRequest(BigDecimal.ZERO, "Unauthorized proposal.")).getStatusCode());
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(new BigDecimal("300"));
        paymentRepository.save(payment);
        ResponseEntity<?> proposalResponse = controller.proposePrice(
                booking.getId(), testJwt(provider, "PROVIDER"),
                new PriceProposalRequest(BigDecimal.ZERO, "Provider rate proposal."));
        assertEquals(HttpStatus.CREATED, proposalResponse.getStatusCode());
        BookingPriceHistory proposal = priceHistoryRepository
                .findFirstByBooking_IdAndStatusOrderByCreatedAtDesc(booking.getId(), "PENDING_APPROVAL")
                .orElseThrow();

        ResponseEntity<?> response = controller.decidePriceProposal(
                booking.getId(), proposal.getId(), testJwt(otherCustomer, "CUSTOMER"),
                new PriceProposalDecisionRequest("APPROVED"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(new java.math.BigDecimal("300.00"),
                bookingRepository.findById(booking.getId()).orElseThrow().getPrice());
        assertEquals("PENDING_APPROVAL",
                priceHistoryRepository.findById(proposal.getId()).orElseThrow().getStatus());
    }

    @Test
    void newCustomerRequestIsSavedAsPendingAndVisibleToMatchingProvider() {
        User customer = saveUser("Customer", "customer@example.com", "CUSTOMER", null);
        User provider = saveUser("Plumbing Pro", "plumber@example.com", "PROVIDER", "PLUMBING");
        saveUser("Electrician", "electrician@example.com", "PROVIDER", "ELECTRICAL");

        ServiceRequest request = new ServiceRequest();
        request.setCustomerName("Customer");
        request.setService("PLUMBING");
        request.setPhone("9876543210");
        request.setAddress("10 Main Street");
        request.setPreferredDate("2026-11-01");

        ResponseEntity<?> createResponse =
                controller.createServiceRequest(testJwt(customer, "CUSTOMER"), request);

        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        BookingEntity created = assertInstanceOf(
                BookingEntity.class,
                createResponse.getBody()
        );
        assertNotNull(created.getId());
        assertEquals("PENDING", created.getStatus());
        assertEquals(new java.math.BigDecimal("300.00"), created.getPrice());
        BookingPriceHistory initialEstimate = priceHistoryRepository
                .findFirstByBooking_IdAndStatusOrderByCreatedAtDesc(created.getId(), "APPROVED")
                .orElseThrow();
        assertEquals(new BigDecimal("300.00"), initialEstimate.getProposedTotal());

        ResponseEntity<?> providerResponse =
                controller.getServiceRequests(testJwt(provider, "PROVIDER"));
        List<?> matchingRequests =
                assertInstanceOf(List.class, providerResponse.getBody());

        assertEquals(1, matchingRequests.size());
        BookingEntity visibleRequest = assertInstanceOf(
                BookingEntity.class,
                matchingRequests.get(0)
        );
        assertEquals(created.getId(), visibleRequest.getId());
    }

    @Test
    void customerProblemPhotoIsSavedAndAvailableOnlyToBookingParticipants() {
        User customer = saveUser("Photo Customer", "photo-customer@example.com", "CUSTOMER", null);
        User assignedProvider = saveUser(
                "Photo Provider", "photo-provider@example.com", "PROVIDER", "PLUMBING");
        User otherProvider = saveUser(
                "Other Provider", "other-photo-provider@example.com", "PROVIDER", "PLUMBING");
        byte[] imageBytes = new byte[128 * 1024];
        byte[] pngSignature = {
                (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a,
        };
        System.arraycopy(pngSignature, 0, imageBytes, 0, pngSignature.length);
        imageBytes[imageBytes.length - 1] = 1;

        ServiceRequest request = new ServiceRequest();
        request.setCustomerName("Photo Customer");
        request.setService("PLUMBING");
        request.setPhone("9876543210");
        request.setAddress("10 Main Street");
        request.setPreferredDate("2026-11-01");
        request.setNotes("Water is leaking under the kitchen sink");
        request.setProblemImageBase64(java.util.Base64.getEncoder().encodeToString(imageBytes));

        ResponseEntity<?> createResponse = controller.createServiceRequest(
                testJwt(customer, "CUSTOMER"), request);
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        BookingEntity created = assertInstanceOf(BookingEntity.class, createResponse.getBody());
        BookingEntity saved = bookingRepository.findById(created.getId()).orElseThrow();
        assertEquals("Water is leaking under the kitchen sink", saved.getNotes());
        assertArrayEquals(imageBytes, saved.getProblemImageData());
        assertEquals("image/png", saved.getProblemImageContentType());

        ResponseEntity<?> eligibleProviderImage = controller.problemImage(
                saved.getId(), testJwt(assignedProvider, "PROVIDER"));
        assertEquals(HttpStatus.OK, eligibleProviderImage.getStatusCode());
        assertEquals(true, photoViewRepository.existsByBooking_IdAndProvider_Id(
                saved.getId(), assignedProvider.getId()));

        saved.setProvider(assignedProvider);
        saved.setProviderName(assignedProvider.getFullName());
        saved.setStatus("ACCEPTED");
        bookingRepository.save(saved);

        ResponseEntity<?> providerBookings = controller.getServiceRequests(
                testJwt(assignedProvider, "PROVIDER"));
        List<?> assignedBookings = assertInstanceOf(List.class, providerBookings.getBody());
        BookingEntity providerView = assertInstanceOf(BookingEntity.class, assignedBookings.get(0));
        assertEquals(true, providerView.getProblemImageAvailable());

        ResponseEntity<?> assignedProviderImage = controller.problemImage(
                saved.getId(), testJwt(assignedProvider, "PROVIDER"));
        assertEquals(HttpStatus.OK, assignedProviderImage.getStatusCode());
        assertArrayEquals(imageBytes,
                assertInstanceOf(byte[].class, assignedProviderImage.getBody()));
        assertEquals("image/png", assignedProviderImage.getHeaders().getContentType().toString());

        ResponseEntity<?> unauthorizedImage = controller.problemImage(
                saved.getId(), testJwt(otherProvider, "PROVIDER"));
        assertEquals(HttpStatus.NOT_FOUND, unauthorizedImage.getStatusCode());

        ResponseEntity<?> customerImage = controller.problemImage(
                saved.getId(), testJwt(customer, "CUSTOMER"));
        assertEquals(HttpStatus.OK, customerImage.getStatusCode());
    }

    @Test
    void invalidProblemPhotoIsRejectedBeforeBookingIsSaved() {
        User customer = saveUser("Photo Customer", "invalid-photo@example.com", "CUSTOMER", null);
        ServiceRequest request = new ServiceRequest();
        request.setCustomerName("Photo Customer");
        request.setService("PLUMBING");
        request.setPhone("9876543210");
        request.setAddress("10 Main Street");
        request.setPreferredDate("2026-11-01");
        request.setProblemImageBase64(java.util.Base64.getEncoder().encodeToString(
                "not-an-image".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        ResponseEntity<?> response = controller.createServiceRequest(
                testJwt(customer, "CUSTOMER"), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(0, bookingRepository.count());
    }

    @Test
    void acceptingRequestPersistsProviderAndStatusAndRejectsSecondProvider() {
        User customer = saveUser("Customer", "customer@example.com", "CUSTOMER", null);
        User firstProvider = saveUser(
                "First Plumber",
                "first@example.com",
                "PROVIDER",
                "PLUMBER"
        );
        firstProvider.setExperience("5_PLUS_YEARS");
        firstProvider.setServiceArea("Central district");
        firstProvider = userRepository.save(firstProvider);
        ProviderProfile firstProfile = providerProfileRepository.findByUser_Id(firstProvider.getId())
                .orElseThrow();
        firstProfile.setProfessionalSummary("Licensed plumbing professional.");
        providerProfileRepository.save(firstProfile);
        User secondProvider = saveUser(
                "Second Plumber",
                "second@example.com",
                "PROVIDER",
                "PLUMBING"
        );

        BookingEntity booking = bookingRepository.save(new BookingEntity(
                "HF100001",
                "Customer",
                "PLUMBING",
                "Provider will be assigned",
                "PENDING",
                customer
        ));
        booking.setPreferredDate(java.time.LocalDate.now().plusDays(2).toString());
        booking.setPreferredAt(java.time.LocalDate.now().plusDays(2)
                .atTime(java.time.LocalTime.of(9, 0)));
        bookingRepository.save(booking);

        ResponseEntity<?> firstResponse =
                controller.acceptRequest(
                        booking.getId(),
                        testJwt(firstProvider, "PROVIDER")
                );

        assertEquals(
                HttpStatus.OK,
                firstResponse.getStatusCode(),
                String.valueOf(firstResponse.getBody())
        );

        BookingEntity persisted = bookingRepository.findById(booking.getId())
                .orElseThrow();
        assertEquals("ACCEPTED", persisted.getStatus());
        assertNotNull(persisted.getProvider());
        assertEquals(firstProvider.getId(), persisted.getProvider().getId());
        assertEquals("First Plumber", persisted.getProviderName());
        BookingEntity acceptedResponse = assertInstanceOf(BookingEntity.class, firstResponse.getBody());
        assertEquals("5_PLUS_YEARS", acceptedResponse.getProviderExperience());
        assertEquals("Central district", acceptedResponse.getProviderServiceArea());
        assertEquals("Licensed plumbing professional.",
                acceptedResponse.getProviderProfessionalSummary());

        ResponseEntity<?> duplicateResponse =
                controller.acceptRequest(
                        booking.getId(),
                        testJwt(secondProvider, "PROVIDER")
                );

        assertEquals(HttpStatus.CONFLICT, duplicateResponse.getStatusCode());

        ResponseEntity<?> customerResponse =
                controller.getServiceRequests(testJwt(customer, "CUSTOMER"));
        List<?> customerBookings =
                assertInstanceOf(List.class, customerResponse.getBody());
        BookingEntity customerBooking = assertInstanceOf(
                BookingEntity.class,
                customerBookings.get(0)
        );
        assertEquals("ACCEPTED", customerBooking.getStatus());
        assertEquals("First Plumber", customerBooking.getProviderName());
        assertEquals(firstProvider.getId(), customerBooking.getProvider().getId());
    }

    @Test
    void providerMustLoadCustomerProblemPhotoBeforeAcceptingRequest() {
        User customer = saveUser("Photo Customer", "photo-gate-customer@example.com", "CUSTOMER", null);
        User provider = saveUser(
                "Photo Gate Provider", "photo-gate-provider@example.com", "PROVIDER", "PLUMBING");
        BookingEntity booking = new BookingEntity(
                "HF_PHOTO_GATE_1", "Photo Customer", "PLUMBING",
                "Provider will be assigned", "PENDING", customer);
        booking.setProblemImageData(new byte[] {
                (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3,
        });
        booking.setProblemImageContentType("image/png");
        booking.setPreferredDate(java.time.LocalDate.now().plusDays(2).toString());
        booking.setPreferredAt(java.time.LocalDate.now().plusDays(2)
                .atTime(java.time.LocalTime.of(9, 0)));
        bookingRepository.save(booking);

        ResponseEntity<?> prematureAccept = controller.acceptRequest(
                booking.getId(), testJwt(provider, "PROVIDER"));
        assertEquals(HttpStatus.CONFLICT, prematureAccept.getStatusCode());

        ResponseEntity<?> photoResponse = controller.problemImage(
                booking.getId(), testJwt(provider, "PROVIDER"));
        assertEquals(HttpStatus.OK, photoResponse.getStatusCode());

        ResponseEntity<?> accepted = controller.acceptRequest(
                booking.getId(), testJwt(provider, "PROVIDER"));
        assertEquals(HttpStatus.OK, accepted.getStatusCode());
    }

    @Test
    void bookingIdentifierIsUniqueInTheDatabase() {
        User customer = saveUser("Customer", "customer@example.com", "CUSTOMER", null);
        bookingRepository.saveAndFlush(new BookingEntity(
                "HF_UNIQUE_1", "Customer", "PLUMBING",
                "Provider will be assigned", "PENDING", customer));

        assertThrows(DataIntegrityViolationException.class, () ->
                bookingRepository.saveAndFlush(new BookingEntity(
                        "HF_UNIQUE_1", "Customer", "PLUMBING",
                        "Provider will be assigned", "PENDING", customer)));
    }

    @Test
    void concurrentProvidersCannotAcceptTheSameBooking() throws Exception {
        User customer = saveUser("Customer", "customer@example.com", "CUSTOMER", null);
        User firstProvider = saveUser("First Plumber", "first@example.com", "PROVIDER", "PLUMBING");
        User secondProvider = saveUser("Second Plumber", "second@example.com", "PROVIDER", "PLUMBING");
        java.time.LocalDate appointmentDate = java.time.LocalDate.now().plusDays(2);
        BookingEntity booking = new BookingEntity(
                "HF_RACE_1", "Customer", "PLUMBING",
                "Provider will be assigned", "PENDING", customer);
        booking.setPreferredDate(appointmentDate.toString());
        booking.setPreferredAt(appointmentDate.atTime(9, 0));
        bookingRepository.saveAndFlush(booking);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<?>> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return controller.acceptRequest(booking.getId(), testJwt(firstProvider, "PROVIDER"));
            });
            Future<ResponseEntity<?>> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return controller.acceptRequest(booking.getId(), testJwt(secondProvider, "PROVIDER"));
            });

            assertEquals(true, ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            ResponseEntity<?> firstResult = first.get(10, TimeUnit.SECONDS);
            ResponseEntity<?> secondResult = second.get(10, TimeUnit.SECONDS);
            long accepted = List.of(firstResult, secondResult).stream()
                    .filter(result -> result.getStatusCode() == HttpStatus.OK)
                    .count();
            long conflicts = List.of(firstResult, secondResult).stream()
                    .filter(result -> result.getStatusCode() == HttpStatus.CONFLICT)
                    .count();

            assertEquals(1, accepted);
            assertEquals(1, conflicts);
            assertEquals("ACCEPTED", bookingRepository.findById(booking.getId())
                    .orElseThrow().getStatus());
            assertEquals(1, historyRepository.findByBooking_IdOrderByChangedAtAsc(booking.getId())
                    .stream().filter(entry -> "ACCEPTED".equals(entry.getNewStatus())).count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void providerDismissalIsPersistedAndDoesNotHideRequestFromOtherProviders() {
        User customer = saveUser("Customer", "customer@example.com", "CUSTOMER", null);
        User firstProvider = saveUser(
                "First Plumber", "first@example.com", "PROVIDER", "PLUMBER"
        );
        User secondProvider = saveUser(
                "Second Plumber", "second@example.com", "PROVIDER", "PLUMBING"
        );
        BookingEntity booking = bookingRepository.save(new BookingEntity(
                "HF_DISMISS_1",
                "Customer",
                "PLUMBING",
                "Provider will be assigned",
                "PENDING",
                customer
        ));
        booking.setPreferredDate(java.time.LocalDate.now().plusDays(2).toString());
        booking.setPreferredAt(java.time.LocalDate.now().plusDays(2)
                .atTime(java.time.LocalTime.of(9, 0)));
        bookingRepository.save(booking);

        ResponseEntity<?> dismissResponse = controller.rejectRequest(
                booking.getId(),
                testJwt(firstProvider, "PROVIDER")
        );
        assertEquals(HttpStatus.OK, dismissResponse.getStatusCode());
        assertEquals(1, dismissalRepository.count());

        List<?> firstProviderBookings = (List<?>) controller
                .getServiceRequests(testJwt(firstProvider, "PROVIDER"))
                .getBody();
        List<?> secondProviderBookings = (List<?>) controller
                .getServiceRequests(testJwt(secondProvider, "PROVIDER"))
                .getBody();
        assertEquals(0, firstProviderBookings.size());
        assertEquals(1, secondProviderBookings.size());
        assertEquals("PENDING", bookingRepository.findById(booking.getId())
                .orElseThrow().getStatus());
    }

    @Test
    void pendingRequestsRespectProviderRadiusAvailabilityAndOverlappingJobs() {
        User customer = saveUser("Customer", "customer@example.com", "CUSTOMER", null);
        User provider = saveUser(
                "Nearby Plumber", "nearby@example.com", "PROVIDER", "PLUMBING"
        );
        ProviderProfile profile = providerProfileRepository.findByUser_Id(provider.getId())
                .orElseThrow();
        profile.setLatitude(19.0760);
        profile.setLongitude(72.8777);
        profile.setServiceRadiusKm(10);
        providerProfileRepository.save(profile);

        java.time.LocalDate appointmentDate = java.time.LocalDate.now().plusDays(2);
        BookingEntity request = new BookingEntity(
                "HF_RADIUS_1", "Customer", "PLUMBING",
                "Provider will be assigned", "PENDING", customer
        );
        request.setPreferredDate(appointmentDate.toString());
        request.setPreferredAt(appointmentDate.atTime(9, 0));
        request.setLatitude(18.5204);
        request.setLongitude(73.8567);
        bookingRepository.save(request);

        List<?> farRequests = (List<?>) controller
                .getServiceRequests(testJwt(provider, "PROVIDER")).getBody();
        assertEquals(0, farRequests.size());

        request.setLatitude(19.0765);
        request.setLongitude(72.8780);
        bookingRepository.save(request);
        List<?> nearbyRequests = (List<?>) controller
                .getServiceRequests(testJwt(provider, "PROVIDER")).getBody();
        assertEquals(1, nearbyRequests.size());

        BookingEntity conflictingJob = new BookingEntity(
                "HF_OVERLAP_1", "Customer", "PLUMBING",
                "Nearby Plumber", "ACCEPTED", customer
        );
        conflictingJob.setProvider(provider);
        conflictingJob.setPreferredDate(appointmentDate.toString());
        conflictingJob.setPreferredAt(appointmentDate.atTime(9, 30));
        bookingRepository.save(conflictingJob);
        List<?> busyRequests = (List<?>) controller
                .getServiceRequests(testJwt(provider, "PROVIDER")).getBody();
        assertEquals(2, busyRequests.size());
        List<Long> visibleBookingIds = busyRequests.stream()
                .map(BookingEntity.class::cast)
                .map(BookingEntity::getId)
                .toList();
        assertEquals(true, visibleBookingIds.contains(request.getId()));
        assertEquals(true, visibleBookingIds.contains(conflictingJob.getId()));
        BookingEntity visibleRequest = busyRequests.stream()
                .map(BookingEntity.class::cast)
                .filter(booking -> booking.getId().equals(request.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals("OVERLAPPING_JOB", visibleRequest.getProviderRequestIssue());
    }

    @Test
    void providerWithoutSavedCoordinatesCanSeeAndAcceptLocationTaggedRequest() {
        User customer = saveUser("Customer", "unknown-location-customer@example.com", "CUSTOMER", null);
        User provider = saveUser(
                "Plumber Without Location", "unknown-location-provider@example.com",
                "PROVIDER", "PLUMBING"
        );
        java.time.LocalDate appointmentDate = java.time.LocalDate.now().plusDays(2);
        BookingEntity request = new BookingEntity(
                "HF_NO_LOC_1", "Customer", "PLUMBING",
                "Provider will be assigned", "PENDING", customer
        );
        request.setPreferredDate(appointmentDate.toString());
        request.setPreferredAt(appointmentDate.atTime(9, 0));
        request.setLatitude(19.0760);
        request.setLongitude(72.8777);
        bookingRepository.save(request);

        List<?> providerRequests = (List<?>) controller
                .getServiceRequests(testJwt(provider, "PROVIDER")).getBody();
        assertEquals(1, providerRequests.size());
        BookingEntity visibleRequest = assertInstanceOf(
                BookingEntity.class, providerRequests.get(0));
        assertEquals(request.getId(), visibleRequest.getId());

        ResponseEntity<?> accepted = controller.acceptRequest(
                request.getId(), testJwt(provider, "PROVIDER"));
        assertEquals(HttpStatus.OK, accepted.getStatusCode(), String.valueOf(accepted.getBody()));
    }

    @Test
    void acRepairRequestIsVisibleEvenWhenProviderNeedsToUpdateAvailability() {
        User customer = saveUser("Customer", "ac-request-customer@example.com", "CUSTOMER", null);
        User provider = saveUser(
                "AC Repair Provider", "ac-request-provider@example.com",
                "PROVIDER", "AC_REPAIR"
        );
        ProviderProfile profile = providerProfileRepository.findByUser_Id(provider.getId())
                .orElseThrow();
        java.time.LocalDate appointmentDate = java.time.LocalDate.now().plusDays(2);
        availabilityRepository.findByProvider_IdAndDayOfWeekAndEnabledTrue(
                        profile.getId(), appointmentDate.getDayOfWeek())
                .forEach(slot -> {
                    slot.setEnabled(false);
                    availabilityRepository.save(slot);
                });

        BookingEntity request = new BookingEntity(
                "HF_AC_REQ_1", "Customer", "AC REPAIR",
                "Provider will be assigned", "PENDING", customer
        );
        request.setPreferredDate(appointmentDate.toString());
        request.setPreferredAt(appointmentDate.atTime(14, 0));
        bookingRepository.save(request);

        List<?> providerRequests = (List<?>) controller
                .getServiceRequests(testJwt(provider, "PROVIDER")).getBody();
        assertEquals(1, providerRequests.size());
        BookingEntity visibleRequest = assertInstanceOf(
                BookingEntity.class, providerRequests.get(0));
        assertEquals(request.getId(), visibleRequest.getId());
        assertEquals("OUTSIDE_AVAILABILITY", visibleRequest.getProviderRequestIssue());

        ResponseEntity<?> acceptResponse = controller.acceptRequest(
                request.getId(), testJwt(provider, "PROVIDER"));
        assertEquals(HttpStatus.CONFLICT, acceptResponse.getStatusCode());
    }

    @Test
    void invalidPastDateIsRejectedWithoutCreatingBooking() {
        User customer = saveUser("Customer", "customer@example.com", "CUSTOMER", null);
        ServiceRequest request = new ServiceRequest();
        request.setCustomerName("Customer");
        request.setService("PLUMBING");
        request.setPhone("9876543210");
        request.setAddress("10 Main Street");
        request.setPreferredDate("2020-01-01");

        ResponseEntity<?> response = controller.createServiceRequest(
                testJwt(customer, "CUSTOMER"),
                request
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(0, bookingRepository.count());
    }

    @Test
    void customerCanCancelOnlyTheirEligibleBookings() {
        User customer = saveUser("Customer", "customer@example.com", "CUSTOMER", null);
        User otherCustomer = saveUser(
                "Other Customer", "other-customer@example.com", "CUSTOMER", null
        );
        BookingEntity booking = new BookingEntity(
                "HF_CANCEL_1", "Customer", "PLUMBING",
                "Provider will be assigned", "PENDING", customer
        );
        booking.setPreferredDate(java.time.LocalDate.now().plusDays(2).toString());
        bookingRepository.save(booking);

        ResponseEntity<?> otherCustomerResponse = controller.cancelBooking(
                booking.getId(),
                testJwt(otherCustomer, "CUSTOMER")
        );
        assertEquals(HttpStatus.FORBIDDEN, otherCustomerResponse.getStatusCode());

        ResponseEntity<?> cancelResponse = controller.cancelBooking(
                booking.getId(),
                testJwt(customer, "CUSTOMER")
        );
        assertEquals(HttpStatus.OK, cancelResponse.getStatusCode());
        BookingEntity cancelled = assertInstanceOf(BookingEntity.class, cancelResponse.getBody());
        assertEquals("CANCELLED", cancelled.getStatus());

        ResponseEntity<?> repeatedCancellation = controller.cancelBooking(
                booking.getId(),
                testJwt(customer, "CUSTOMER")
        );
        assertEquals(HttpStatus.CONFLICT, repeatedCancellation.getStatusCode());
    }

    @Test
    void assignedProviderCanCancelBeforePinButNotAfterJobStarts() {
        User customer = saveUser("Cancel Customer", "cancel-customer@example.com", "CUSTOMER", null);
        User assignedProvider = saveUser(
                "Cancel Provider", "cancel-provider@example.com", "PROVIDER", "PLUMBING");
        User otherProvider = saveUser(
                "Other Cancel Provider", "other-cancel-provider@example.com", "PROVIDER", "PLUMBING");

        BookingEntity arrivedBooking = new BookingEntity(
                "HF_PROVIDER_CANCEL_1", "Cancel Customer", "PLUMBING",
                assignedProvider.getFullName(), "ARRIVED", customer);
        arrivedBooking.setProvider(assignedProvider);
        bookingRepository.save(arrivedBooking);

        ResponseEntity<?> otherProviderResponse = controller.cancelBooking(
                arrivedBooking.getId(), testJwt(otherProvider, "PROVIDER"));
        assertEquals(HttpStatus.FORBIDDEN, otherProviderResponse.getStatusCode());

        ResponseEntity<?> providerResponse = controller.cancelBooking(
                arrivedBooking.getId(), testJwt(assignedProvider, "PROVIDER"));
        assertEquals(HttpStatus.OK, providerResponse.getStatusCode());
        BookingEntity cancelled = assertInstanceOf(BookingEntity.class, providerResponse.getBody());
        assertEquals("CANCELLED", cancelled.getStatus());

        BookingEntity startedBooking = new BookingEntity(
                "HF_PROVIDER_CANCEL_2", "Cancel Customer", "PLUMBING",
                assignedProvider.getFullName(), "IN_PROGRESS", customer);
        startedBooking.setProvider(assignedProvider);
        startedBooking.markCompletionPinUsed();
        bookingRepository.save(startedBooking);

        ResponseEntity<?> afterStartResponse = controller.cancelBooking(
                startedBooking.getId(), testJwt(assignedProvider, "PROVIDER"));
        assertEquals(HttpStatus.CONFLICT, afterStartResponse.getStatusCode());
        assertEquals("IN_PROGRESS",
                bookingRepository.findById(startedBooking.getId()).orElseThrow().getStatus());
    }

    @Test
    void onlyAssignedProviderCanPerformAllowedBookingStatusTransitions() {
        User customer = saveUser("Customer", "customer@example.com", "CUSTOMER", null);
        User assignedProvider = saveUser(
                "Assigned Plumber", "assigned@example.com", "PROVIDER", "PLUMBING"
        );
        User otherProvider = saveUser(
                "Other Plumber", "other@example.com", "PROVIDER", "PLUMBING"
        );
        BookingEntity booking = new BookingEntity(
                "HF_STATUS_1", "Customer", "PLUMBING",
                assignedProvider.getFullName(), "ACCEPTED", customer
        );
        booking.setProvider(assignedProvider);
        booking.setPreferredDate(java.time.LocalDate.now().plusDays(2).toString());
        booking.setPrice(300);
        String startPin = "426813";
        booking.setCompletionPinHash(bookingPinService.hash(startPin));
        booking.setCompletionPinEncrypted(bookingPinService.encrypt(startPin));
        bookingRepository.save(booking);
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(java.math.BigDecimal.valueOf(300));
        payment.setMethod("CASH");
        paymentRepository.save(payment);

        ResponseEntity<?> otherProviderResponse = controller.updateProviderStatus(
                booking.getId(),
                testJwt(otherProvider, "PROVIDER"),
                new BookingStatusUpdateRequest("CONFIRMED", "")
        );
        assertEquals(HttpStatus.FORBIDDEN, otherProviderResponse.getStatusCode());

        ResponseEntity<?> invalidTransition = controller.updateProviderStatus(
                booking.getId(),
                testJwt(assignedProvider, "PROVIDER"),
                new BookingStatusUpdateRequest("COMPLETED", "")
        );
        assertEquals(HttpStatus.CONFLICT, invalidTransition.getStatusCode());

        for (String status : List.of(
                "CONFIRMED", "ON_THE_WAY", "ARRIVED", "IN_PROGRESS", "COMPLETED")) {
            ResponseEntity<?> response = controller.updateProviderStatus(
                    booking.getId(),
                    testJwt(assignedProvider, "PROVIDER"),
                    new BookingStatusUpdateRequest(
                            status, "", null, "IN_PROGRESS".equals(status) ? startPin : null,
                            "COMPLETED".equals(status) ? "CASH" : null,
                            "COMPLETED".equals(status) ? new BigDecimal("300.00") : null)
            );
            assertEquals(HttpStatus.OK, response.getStatusCode());
            BookingEntity updated = assertInstanceOf(BookingEntity.class, response.getBody());
            assertEquals(status, updated.getStatus());
            if ("COMPLETED".equals(status)) {
                assertEquals(new BigDecimal("300.00"), updated.getPaymentCollectedAmount());
            }
        }

        assertEquals(
                List.of("CONFIRMED", "ON_THE_WAY", "ARRIVED",
                        "IN_PROGRESS", "COMPLETED"),
                historyRepository.findByBooking_IdOrderByChangedAtAsc(booking.getId())
                        .stream()
                        .map(entry -> entry.getNewStatus())
                        .toList()
        );
        assertEquals(1, invoiceRepository.count());
        Payment paidPayment = paymentRepository.findByBooking_Id(booking.getId()).orElseThrow();
        assertEquals("PAID", paidPayment.getStatus());
        assertEquals("CASH", paidPayment.getMethod());
        assertEquals(new BigDecimal("300.00"), paidPayment.getAmountCollected());
        assertNotNull(bookingRepository.findById(booking.getId())
                .orElseThrow().getCompletionPinUsedAt());
    }

    @Test
    void providerCannotCompleteWhileAnExtraChargeAwaitsCustomerApproval() {
        User customer = saveUser("Charge Customer", "charge-customer@example.com", "CUSTOMER", null);
        User provider = saveUser("Charge Provider", "charge-provider@example.com", "PROVIDER", "PLUMBING");
        BookingEntity booking = new BookingEntity(
                "HF_CHARGE_PENDING", customer.getFullName(), "PLUMBING",
                provider.getFullName(), "IN_PROGRESS", customer);
        booking.setProvider(provider);
        booking.setPrice(300);
        bookingRepository.save(booking);

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(new BigDecimal("300.00"));
        payment.setMethod("CASH");
        paymentRepository.save(payment);

        BookingPriceHistory proposal = new BookingPriceHistory();
        proposal.setBooking(booking);
        proposal.setActor(provider);
        proposal.setBaseAmount(new BigDecimal("300.00"));
        proposal.setProposedTotal(new BigDecimal("360.00"));
        proposal.setStatus("PENDING_APPROVAL");
        proposal.setExplanation("Tools: Replacement wrench");
        priceHistoryRepository.save(proposal);

        ResponseEntity<?> response = controller.updateProviderStatus(
                booking.getId(),
                testJwt(provider, "PROVIDER"),
                new BookingStatusUpdateRequest(
                        "COMPLETED", "", null, null, "CASH", new BigDecimal("360.00")));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("IN_PROGRESS", bookingRepository.findById(booking.getId())
                .orElseThrow().getStatus());
        assertEquals("UNPAID", paymentRepository.findByBooking_Id(booking.getId())
                .orElseThrow().getStatus());
        assertEquals(0, invoiceRepository.count());
    }

    @Test
    void activeProviderCanProgressAnAlreadyAssignedBookingDuringReverification() {
        User customer = saveUser("Customer", "reverify-customer@example.com", "CUSTOMER", null);
        User provider = saveUser(
                "Assigned Plumber", "reverify-provider@example.com", "PROVIDER", "PLUMBING");
        provider.setProviderApproved(false);
        userRepository.save(provider);
        ProviderProfile profile = providerProfileRepository.findByUser_Id(provider.getId())
                .orElseThrow();
        profile.setVerificationStatus("PENDING");
        providerProfileRepository.save(profile);

        BookingEntity booking = new BookingEntity(
                "HF_REVERIFY_ACTIVE_1", "Customer", "PLUMBING",
                provider.getFullName(), "ACCEPTED", customer);
        booking.setProvider(provider);
        booking.setPrice(300);
        bookingRepository.save(booking);

        ResponseEntity<?> response = controller.updateProviderStatus(
                booking.getId(),
                testJwt(provider, "PROVIDER"),
                new BookingStatusUpdateRequest("CONFIRMED", ""));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        BookingEntity updated = assertInstanceOf(BookingEntity.class, response.getBody());
        assertEquals("CONFIRMED", updated.getStatus());
    }

    @Test
    void providerMustEnterCorrectCustomerPinBeforeStartingJob() {
        User customer = saveUser("Customer", "customer@example.com", "CUSTOMER", null);
        User provider = saveUser("Assigned Plumber", "assigned@example.com", "PROVIDER", "PLUMBING");
        String startPin = "385204";
        BookingEntity booking = new BookingEntity(
                "HF_PIN_1", "Customer", "PLUMBING", provider.getFullName(), "ON_THE_WAY", customer);
        booking.setProvider(provider);
        booking.setCompletionPinHash(bookingPinService.hash(startPin));
        booking.setCompletionPinEncrypted(bookingPinService.encrypt(startPin));
        bookingRepository.save(booking);

        ResponseEntity<?> incorrectPin = controller.updateProviderStatus(
                booking.getId(), testJwt(provider, "PROVIDER"),
                new BookingStatusUpdateRequest("IN_PROGRESS", "", null, "000000"));

        assertEquals(HttpStatus.BAD_REQUEST, incorrectPin.getStatusCode());
        assertEquals(1, bookingRepository.findById(booking.getId())
                .orElseThrow().getCompletionPinAttempts());
        assertEquals("ON_THE_WAY", bookingRepository.findById(booking.getId())
                .orElseThrow().getStatus());

        ResponseEntity<?> correctPin = controller.updateProviderStatus(
                booking.getId(), testJwt(provider, "PROVIDER"),
                new BookingStatusUpdateRequest("IN_PROGRESS", "", null, startPin));

        assertEquals(HttpStatus.OK, correctPin.getStatusCode());
        assertEquals("IN_PROGRESS", bookingRepository.findById(booking.getId())
                .orElseThrow().getStatus());
    }

    @Test
    void providerReferenceCannotMarkUpiPaymentAsPaid() {
        User customer = saveUser("Customer", "customer@example.com", "CUSTOMER", null);
        User provider = saveUser("Assigned Plumber", "assigned@example.com", "PROVIDER", "PLUMBING");
        BookingEntity booking = new BookingEntity(
                "HF_UPI_1", "Customer", "PLUMBING", provider.getFullName(), "IN_PROGRESS", customer);
        booking.setProvider(provider);
        booking.setPreferredDate(java.time.LocalDate.now().plusDays(2).toString());
        booking.setPrice(300);
        bookingRepository.save(booking);
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(java.math.BigDecimal.valueOf(300));
        payment.setMethod("UPI");
        payment.setMerchantUpiId("homefix@bank");
        paymentRepository.save(payment);

        ResponseEntity<?> completed = controller.updateProviderStatus(
                booking.getId(),
                testJwt(provider, "PROVIDER"),
                new BookingStatusUpdateRequest("COMPLETED", "", "UNVERIFIED-REFERENCE"));

        assertEquals(HttpStatus.OK, completed.getStatusCode());
        Payment paymentAfterCompletion = paymentRepository.findByBooking_Id(booking.getId()).orElseThrow();
        assertEquals("UNPAID", paymentAfterCompletion.getStatus());
        assertEquals("UPI", paymentAfterCompletion.getMethod());
        assertEquals(null, paymentAfterCompletion.getExternalReference());
        assertEquals(null, paymentAfterCompletion.getAmountCollected());
        assertEquals("COMPLETED", bookingRepository.findById(booking.getId())
                .orElseThrow().getStatus());
    }

    @Test
    void providerCannotCompleteCashBookingWithLessThanPayableAmount() {
        User customer = saveUser("Customer", "cash-customer@example.com", "CUSTOMER", null);
        User provider = saveUser("Cash Provider", "cash-provider@example.com", "PROVIDER", "PLUMBING");
        BookingEntity booking = new BookingEntity(
                "HF_CASH_SHORT", "Customer", "PLUMBING", provider.getFullName(), "IN_PROGRESS", customer);
        booking.setProvider(provider);
        booking.setPreferredDate(java.time.LocalDate.now().plusDays(2).toString());
        booking.setPrice(300);
        bookingRepository.save(booking);
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(new BigDecimal("300.00"));
        payment.setMethod("UPI");
        paymentRepository.save(payment);

        ResponseEntity<?> response = controller.updateProviderStatus(
                booking.getId(),
                testJwt(provider, "PROVIDER"),
                new BookingStatusUpdateRequest(
                        "COMPLETED", "", null, null, "CASH", new BigDecimal("299.99")));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("IN_PROGRESS", bookingRepository.findById(booking.getId())
                .orElseThrow().getStatus());
        Payment unchanged = paymentRepository.findByBooking_Id(booking.getId()).orElseThrow();
        assertEquals("UNPAID", unchanged.getStatus());
        assertEquals("UPI", unchanged.getMethod());
        assertEquals(null, unchanged.getAmountCollected());
    }

    @Test
    void customerCanRotateAnUnusedPinBeforeJobStarts() {
        User customer = saveUser("Customer", "customer@example.com", "CUSTOMER", null);
        User provider = saveUser("Assigned Plumber", "assigned@example.com", "PROVIDER", "PLUMBING");
        BookingEntity booking = new BookingEntity(
                "HF_PIN_RESET", "Customer", "PLUMBING", provider.getFullName(), "ON_THE_WAY", customer);
        booking.setProvider(provider);
        booking.setCompletionPinHash(bookingPinService.hash("123456"));
        booking.setCompletionPinEncrypted(bookingPinService.encrypt("123456"));
        for (int attempt = 0; attempt < 5; attempt++) {
            booking.incrementCompletionPinAttempts();
        }
        bookingRepository.save(booking);

        ResponseEntity<?> response = controller.resetCompletionPin(
                booking.getId(), testJwt(customer, "CUSTOMER"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        String newPin = (String) body.get("completionPin");
        BookingEntity updated = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(0, updated.getCompletionPinAttempts());
        assertEquals(false, bookingPinService.matches("123456", updated.getCompletionPinHash()));
        assertEquals(true, bookingPinService.matches(newPin, updated.getCompletionPinHash()));
    }

    private User saveUser(
            String name,
            String email,
            String role,
            String serviceCategory
    ) {
        User user = new User(name, email, "test-password");
        user.setRole(role);
        user.setServiceCategory(serviceCategory);
        user.setProviderApproved("PROVIDER".equals(role));
        User saved = userRepository.save(user);
        if ("PROVIDER".equals(role)) {
            ProviderProfile profile = new ProviderProfile(saved);
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
                availabilityRepository.save(slot);
            }
        }
        return saved;
    }

    private Jwt testJwt(User user, String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(user.getId().toString())
                .claim("role", role)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
    }
}
