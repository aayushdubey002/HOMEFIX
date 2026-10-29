package com.homefix.api.controller;

import com.homefix.api.dto.ServiceRequest;
import com.homefix.api.model.BookingDismissal;
import com.homefix.api.model.BookingEntity;
import com.homefix.api.model.BookingStatusHistory;
import com.homefix.api.model.BookingPriceHistory;
import com.homefix.api.model.Invoice;
import com.homefix.api.model.User;
import com.homefix.api.model.ProviderProfile;
import com.homefix.api.model.Payment;
import com.homefix.api.model.ProviderBookingPhotoView;
import com.homefix.api.model.Review;
import com.homefix.api.model.ServiceCategory;
import com.homefix.api.dto.ReviewRequest;
import com.homefix.api.dto.BookingStatusUpdateRequest;
import com.homefix.api.dto.PriceProposalDecisionRequest;
import com.homefix.api.dto.PriceProposalRequest;
import com.homefix.api.repository.BookingStatusHistoryRepository;
import com.homefix.api.repository.BookingPriceHistoryRepository;
import com.homefix.api.repository.InvoiceRepository;
import com.homefix.api.repository.ProviderAvailabilityRepository;
import com.homefix.api.repository.PaymentRepository;
import com.homefix.api.repository.PaymentRefundRepository;
import com.homefix.api.repository.ProviderProfileRepository;
import com.homefix.api.repository.ReviewRepository;
import com.homefix.api.repository.ServiceCategoryRepository;
import com.homefix.api.service.BookingLifecycleService;
import com.homefix.api.service.DistanceCalculator;
import com.homefix.api.repository.BookingRepository;
import com.homefix.api.repository.BookingDismissalRepository;
import com.homefix.api.repository.ProviderBookingPhotoViewRepository;
import com.homefix.api.repository.UserRepository;
import com.homefix.api.service.ServiceCatalog;
import com.homefix.api.service.PaymentConfigurationService;
import com.homefix.api.service.BookingPinService;
import com.homefix.api.service.BookingPricingService;
import com.homefix.api.service.InvoicePdfService;
import com.homefix.api.service.InvoicePdfService.RefundLine;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class ServiceRequestController {

    private static final int MAX_PROBLEM_IMAGE_BYTES = 3 * 1024 * 1024;

    private final BookingRepository bookingRepository;
    private final BookingDismissalRepository dismissalRepository;
    private final ProviderBookingPhotoViewRepository photoViewRepository;
    private final UserRepository userRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final ProviderAvailabilityRepository availabilityRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final BookingStatusHistoryRepository historyRepository;
    private final ReviewRepository reviewRepository;
    private final InvoiceRepository invoiceRepository;
        private final PaymentRepository paymentRepository;
        private final PaymentConfigurationService paymentConfigurationService;
        private final BookingPinService bookingPinService;
    private final BookingLifecycleService lifecycleService;
    private final BookingPriceHistoryRepository priceHistoryRepository;
    private final BookingPricingService bookingPricingService;
    private final InvoicePdfService invoicePdfService;
    private final PaymentRefundRepository paymentRefundRepository;
    private final double maximumServiceRadiusKm;

    public ServiceRequestController(
            BookingRepository bookingRepository,
            BookingDismissalRepository dismissalRepository,
            ProviderBookingPhotoViewRepository photoViewRepository,
            UserRepository userRepository,
            ProviderProfileRepository providerProfileRepository,
            ProviderAvailabilityRepository availabilityRepository,
            ServiceCategoryRepository categoryRepository,
            BookingStatusHistoryRepository historyRepository,
            ReviewRepository reviewRepository,
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            PaymentConfigurationService paymentConfigurationService,
            BookingPinService bookingPinService,
            BookingLifecycleService lifecycleService,
            BookingPriceHistoryRepository priceHistoryRepository,
            BookingPricingService bookingPricingService,
            InvoicePdfService invoicePdfService,
            PaymentRefundRepository paymentRefundRepository,
            @Value("${app.location.service-radius-km:25}") double maximumServiceRadiusKm) {

        this.bookingRepository = bookingRepository;
        this.dismissalRepository = dismissalRepository;
        this.photoViewRepository = photoViewRepository;
        this.userRepository = userRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.availabilityRepository = availabilityRepository;
        this.categoryRepository = categoryRepository;
        this.historyRepository = historyRepository;
        this.reviewRepository = reviewRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.paymentConfigurationService = paymentConfigurationService;
        this.bookingPinService = bookingPinService;
        this.lifecycleService = lifecycleService;
        this.priceHistoryRepository = priceHistoryRepository;
        this.bookingPricingService = bookingPricingService;
        this.invoicePdfService = invoicePdfService;
        this.paymentRefundRepository = paymentRefundRepository;
        this.maximumServiceRadiusKm = maximumServiceRadiusKm;
    }

    // ==========================================
    // CREATE SERVICE REQUEST - CUSTOMER
    // ==========================================

    @PostMapping("/service-requests")
    @Transactional
    public ResponseEntity<?> createServiceRequest(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ServiceRequest request) {

        Long userId = authenticatedUserId(jwt);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid authentication token"));
        }
        User customer = userRepository.findById(userId).orElse(null);

        if (customer == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found"));
        }

        if (!"CUSTOMER".equalsIgnoreCase(customer.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "message",
                            "Only customers can create service requests"
                    ));
        }

        if (request.getService() == null
                || request.getService().isBlank()) {

            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Service is required"));
        }

        String paymentMethod = request.getPaymentMethod() == null
                ? "CASH" : normalize(request.getPaymentMethod());
        if (!Set.of("CASH", "UPI").contains(paymentMethod)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Choose Cash or UPI as the payment method."));
        }
        Map<String, Object> paymentConfiguration = paymentConfigurationService.publicConfiguration();
        String merchantUpiId = "";
        if ("UPI".equals(paymentMethod)
                && !Boolean.TRUE.equals(paymentConfiguration.get("gatewayEnabled"))) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message",
                            "Secure UPI checkout is not configured yet. Choose Cash or try again later."));
        }

        String service = normalize(request.getService());

        ServiceCategory category = categoryRepository.findByServiceCodeIgnoreCase(service)
                .or(() -> categoryRepository.findBySlugIgnoreCase(
                        service.toLowerCase(Locale.ROOT).replace(' ', '-')))
                .orElse(null);
        Integer price = category == null
                ? ServiceCatalog.startingPrice(service)
                : category.getStartingPrice().intValueExact();

        if (price == null || (category != null && !category.isActive())) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Invalid service selected: " + service
                    ));
        }

        if (request.getAddress() == null
                || request.getAddress().isBlank()) {

            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Address is required"));
        }

        if (request.getAddress().trim().length() > 500) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Address must be 500 characters or fewer"));
        }

        if (request.getCustomerName() == null
                || request.getCustomerName().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Customer name is required"));
        }

        if (request.getCustomerName().trim().length() > 120) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Customer name must be 120 characters or fewer"));
        }

        if (request.getPhone() == null
                || !request.getPhone().trim().matches("[0-9]{10}")) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Enter a valid 10-digit phone number"
                    ));
        }

        if (request.getPreferredDate() == null
                || request.getPreferredDate().isBlank()) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Preferred date is required"
                    ));
        }

        if (request.getNotes() != null && request.getNotes().length() > 2000) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Problem description must be 2000 characters or fewer"));
        }
        ProblemImage problemImage;
        try {
            problemImage = decodeProblemImage(request.getProblemImageBase64());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", exception.getMessage()));
        }

        LocalDate preferredDate;
        try {
            preferredDate = LocalDate.parse(request.getPreferredDate());
        } catch (DateTimeParseException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Preferred date must use YYYY-MM-DD format"));
        }
        if (preferredDate.isBefore(LocalDate.now())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Preferred date cannot be in the past"));
        }

        LocalTime preferredTime;
        try {
            preferredTime = request.getPreferredTime() == null
                    || request.getPreferredTime().isBlank()
                    ? LocalTime.of(9, 0)
                    : LocalTime.parse(request.getPreferredTime());
        } catch (DateTimeParseException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Preferred time must use HH:mm format"));
        }
        if ((request.getLatitude() == null) != (request.getLongitude() == null)
                || (request.getLatitude() != null
                && !DistanceCalculator.validCoordinates(
                        request.getLatitude(), request.getLongitude()))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Provide valid latitude and longitude coordinates."));
        }

        String bookingCode = "HF"
                + java.util.UUID.randomUUID().toString()
                        .replace("-", "")
                        .substring(0, 10)
                        .toUpperCase(Locale.ROOT);

        BookingEntity booking = new BookingEntity(
                bookingCode,
                request.getCustomerName().trim(),
                service,
                "Provider will be assigned",
                "PENDING",
                customer
        );

        LocalDateTime preferredAt = LocalDateTime.of(preferredDate, preferredTime);
        BookingPricingService.PriceQuote estimate;
        try {
            estimate = bookingPricingService.initialEstimate(
                    BigDecimal.valueOf(price), preferredAt, service);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
        booking.setPhone(request.getPhone().trim());
        booking.setPrice(estimate.total());
        booking.setAddress(request.getAddress().trim());
        booking.setPreferredDate(request.getPreferredDate());
        booking.setPreferredAt(preferredAt);
        booking.setNotes(request.getNotes());
        if (problemImage != null) {
            booking.setProblemImageData(problemImage.data());
            booking.setProblemImageContentType(problemImage.contentType());
        }
        booking.setLatitude(request.getLatitude());
        booking.setLongitude(request.getLongitude());
        String completionPin = bookingPinService.generate();
        booking.setCompletionPinHash(bookingPinService.hash(completionPin));
        booking.setCompletionPinEncrypted(bookingPinService.encrypt(completionPin));
        booking.setCompletionPin(completionPin);

        BookingEntity savedBooking = bookingRepository.save(booking);
        bookingPricingService.recordInitialEstimate(savedBooking, customer, estimate);
        lifecycleService.created(savedBooking, paymentMethod, merchantUpiId);
        addPaymentDetails(savedBooking);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedBooking);
    }

    @PostMapping("/service-requests/{id}/completion-pin")
    @Transactional
    public ResponseEntity<?> resetCompletionPin(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = authenticatedUserId(jwt);
        User customer = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (customer == null || !"CUSTOMER".equalsIgnoreCase(customer.getRole())
                || !customer.isActive()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Customer account required."));
        }
        BookingEntity booking = bookingRepository.findByIdForUpdate(id).orElse(null);
        if (booking == null || !booking.getUser().getId().equals(customer.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Booking was not found for this account."));
        }
        if (!Set.of("PENDING", "ACCEPTED", "CONFIRMED", "ON THE WAY")
                .contains(normalize(booking.getStatus()))
                || booking.getCompletionPinUsedAt() != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "A new PIN can only be issued before the job starts."));
        }
        String completionPin = bookingPinService.generate();
        booking.setCompletionPinHash(bookingPinService.hash(completionPin));
        booking.setCompletionPinEncrypted(bookingPinService.encrypt(completionPin));
        booking.resetCompletionPinAttempts();
        bookingRepository.save(booking);
        return ResponseEntity.ok(Map.of(
                "completionPin", completionPin,
                "message", "A new job-start PIN was generated. Share it with your provider when they arrive."
        ));
    }

    // ==========================================
    // GET CUSTOMER OR PROVIDER REQUESTS
    // ==========================================

    @GetMapping("/service-requests")
    public ResponseEntity<?> getServiceRequests(
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = authenticatedUserId(jwt);
        String role = jwt == null ? null : jwt.getClaimAsString("role");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid authentication token"));
        }

        if ("CUSTOMER".equals(role)) {
            User customer = userRepository.findById(userId)
                    .orElse(null);

            if (customer == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "User not found"));
            }

            if (!"CUSTOMER".equalsIgnoreCase(customer.getRole())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "message",
                                "Customer account required"
                        ));
            }

            List<BookingEntity> bookings = bookingRepository.findByUserOrderByIdDesc(customer)
                    .stream().map(booking -> {
                        if (booking.getCompletionPinUsedAt() == null
                                && booking.getCompletionPinEncrypted() != null) {
                            booking.setCompletionPin(
                                    bookingPinService.decrypt(booking.getCompletionPinEncrypted()));
                        }
                        booking.setProblemImageAvailable(booking.getProblemImageData() != null);
                        addProviderDetails(booking);
                        if (booking.getProvider() != null) {
                            booking.setProviderPhone(booking.getProvider().getPhone());
                        }
                        return addPricingDetails(addPaymentDetails(booking));
                    }).toList();

            return ResponseEntity.ok(bookings);
        }

        if ("PROVIDER".equals(role)) {
            User provider = userRepository.findById(userId)
                    .orElse(null);

            if (provider == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "message",
                                "Provider user not found"
                        ));
            }

            if (!"PROVIDER".equalsIgnoreCase(provider.getRole())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "message",
                                "Provider account required"
                        ));
            }

            String category = provider.getServiceCategory();
            ProviderProfile profile = providerProfileRepository.findByUser_Id(provider.getId())
                    .orElse(null);

            if (category == null || category.isBlank() || profile == null
                    || !provider.isProviderApproved()
                    || !"APPROVED".equalsIgnoreCase(profile.getVerificationStatus())) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "message",
                                "Provider verification must be approved before receiving requests."
                        ));
            }

            List<BookingEntity> pendingRequests = profile.isOnline()
                    ? bookingRepository
                            .findByProviderIsNullAndStatusIgnoreCaseOrderByIdDesc(
                                    "PENDING")
                    : List.of();

            List<BookingEntity> assignedBookings =
                    bookingRepository.findByProviderOrderByIdDesc(provider);

            Set<Long> dismissedBookingIds = dismissalRepository
                    .findByProvider_Id(provider.getId())
                    .stream()
                    .map(dismissal -> dismissal.getBooking().getId())
                    .collect(java.util.stream.Collectors.toSet());

            LinkedHashMap<Long, BookingEntity> result =
                    new LinkedHashMap<>();

            // Keep schedule and job-conflict checks at acceptance so providers can see requests
            // even when they need to update their availability for the requested time.
            for (BookingEntity booking : pendingRequests) {

                if (categoriesMatch(
                        category,
                        booking.getService())
                        && !dismissedBookingIds.contains(booking.getId())
                        && matchesProviderLocation(profile, booking)) {

                    booking.setProviderRequestIssue(
                            providerRequestIssue(provider, profile, booking));
                    result.put(booking.getId(), booking);
                }
            }

            // Also show bookings already assigned to this provider
            for (BookingEntity booking : assignedBookings) {
                result.put(booking.getId(), booking);
            }

            return ResponseEntity.ok(
                    result.values().stream()
                            .map(booking -> {
                                booking.setProblemImageAvailable(
                                        booking.getProblemImageData() != null);
                                return booking;
                            })
                            .map(this::addPaymentDetails)
                            .map(this::addProviderDetails)
                            .map(this::addPricingDetails)
                            .toList()
            );
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "message", "Account role is not supported"
                ));
    }

    @GetMapping("/service-requests/{id}/problem-image")
    @Transactional
    public ResponseEntity<?> problemImage(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = authenticatedUserId(jwt);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid authentication token."));
        }
        BookingEntity booking = bookingRepository.findById(id).orElse(null);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }
        User viewer = userRepository.findById(userId).orElse(null);
        if (viewer == null || !viewer.isActive()) {
            return ResponseEntity.notFound().build();
        }
        boolean isCustomerOwner = "CUSTOMER".equalsIgnoreCase(viewer.getRole())
                && booking.getUser() != null
                && booking.getUser().getId().equals(viewer.getId());
        boolean isAssignedProvider = "PROVIDER".equalsIgnoreCase(viewer.getRole())
                && booking.getProvider() != null
                && booking.getProvider().getId().equals(viewer.getId());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(viewer.getRole());
        boolean isEligibleForPendingRequest = false;
        if ("PROVIDER".equalsIgnoreCase(viewer.getRole())
                && viewer.isActive()
                && viewer.isProviderApproved()
                && booking.getProvider() == null
                && "PENDING".equalsIgnoreCase(booking.getStatus())) {
            ProviderProfile profile = providerProfileRepository
                    .findByUser_Id(viewer.getId())
                    .orElse(null);
            boolean dismissed = dismissalRepository.findByProvider_Id(viewer.getId())
                    .stream()
                    .anyMatch(dismissal -> dismissal.getBooking().getId().equals(id));
            isEligibleForPendingRequest = profile != null
                    && profile.isOnline()
                    && "APPROVED".equalsIgnoreCase(profile.getVerificationStatus())
                    && !dismissed
                    && categoriesMatch(viewer.getServiceCategory(), booking.getService())
                    && matchesProviderLocation(profile, booking);
        }
        if (!isCustomerOwner && !isAssignedProvider && !isEligibleForPendingRequest && !isAdmin) {
            return ResponseEntity.notFound().build();
        }
        byte[] image = booking.getProblemImageData();
        String contentType = booking.getProblemImageContentType();
        if (image == null || contentType == null
                || !Set.of("image/png", "image/jpeg", "image/webp").contains(contentType)) {
            return ResponseEntity.notFound().build();
        }
        if (isEligibleForPendingRequest) {
            BookingEntity lockedBooking = bookingRepository.findByIdForUpdate(id).orElse(null);
            ProviderProfile currentProfile = providerProfileRepository
                    .findByUser_Id(viewer.getId())
                    .orElse(null);
            boolean stillEligible = lockedBooking != null
                    && lockedBooking.getProvider() == null
                    && "PENDING".equalsIgnoreCase(lockedBooking.getStatus())
                    && currentProfile != null
                    && currentProfile.isOnline()
                    && "APPROVED".equalsIgnoreCase(currentProfile.getVerificationStatus())
                    && categoriesMatch(viewer.getServiceCategory(), lockedBooking.getService())
                    && matchesProviderLocation(currentProfile, lockedBooking)
                    && !dismissalRepository.existsByBooking_IdAndProvider_Id(id, viewer.getId());
            if (!stillEligible) {
                return ResponseEntity.notFound().build();
            }
            if (!photoViewRepository.existsByBooking_IdAndProvider_Id(id, viewer.getId())) {
                photoViewRepository.save(new ProviderBookingPhotoView(lockedBooking, viewer));
            }
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(image);
    }

    @PostMapping("/service-requests/{id}/price-proposals")
    @Transactional
    public ResponseEntity<?> proposePrice(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PriceProposalRequest request) {
        Long userId = authenticatedUserId(jwt);
        User provider = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (provider == null || !provider.isActive() || !provider.isProviderApproved()
                || !"PROVIDER".equalsIgnoreCase(provider.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "An active provider account is required."));
        }
        BookingEntity booking = bookingRepository.findByIdForUpdate(id).orElse(null);
        if (booking == null || booking.getProvider() == null
                || !booking.getProvider().getId().equals(provider.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Assigned booking was not found."));
        }
        ProviderProfile profile = providerProfileRepository.findByUser_Id(provider.getId()).orElse(null);
        if (profile == null || !"APPROVED".equalsIgnoreCase(profile.getVerificationStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "An approved provider profile is required."));
        }
        Payment existingPayment = paymentRepository.findByBooking_Id(id).orElse(null);
        if (existingPayment == null || !"UNPAID".equalsIgnoreCase(existingPayment.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Price proposals are unavailable after payment is recorded."));
        }
        try {
            BookingPriceHistory proposal = bookingPricingService.propose(
                    booking, profile, request.additionalAmount(), request.explanation());
            lifecycleService.priceProposalEvent(
                    booking, provider, booking.getUser(), "BOOKING_PRICE_PROPOSED",
                    "Your provider proposed a revised total of ₹"
                            + proposal.getProposedTotal().toPlainString()
                            + ". Review it before the booking price changes.");
            return ResponseEntity.status(HttpStatus.CREATED).body(priceHistoryView(proposal));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", exception.getMessage()));
        }
    }

    @PutMapping("/service-requests/{id}/price-proposals/{proposalId}")
    @Transactional
    public ResponseEntity<?> decidePriceProposal(
            @PathVariable Long id,
            @PathVariable Long proposalId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PriceProposalDecisionRequest request) {
        Long userId = authenticatedUserId(jwt);
        User customer = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (customer == null || !customer.isActive()
                || !"CUSTOMER".equalsIgnoreCase(customer.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "An active customer account is required."));
        }
        BookingEntity booking = bookingRepository.findByIdForUpdate(id).orElse(null);
        if (booking == null || booking.getUser() == null
                || !booking.getUser().getId().equals(customer.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Booking was not found for this account."));
        }
        BookingPriceHistory proposal = priceHistoryRepository.findByIdAndBooking_Id(proposalId, id)
                .orElse(null);
        if (proposal == null || !"PENDING_APPROVAL".equals(proposal.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "This price proposal is no longer awaiting a decision."));
        }
        String decision = request.decision();
        if ("APPROVED".equals(decision)) {
            if (!Set.of("ACCEPTED", "CONFIRMED", "ON_THE_WAY", "ARRIVED", "IN_PROGRESS")
                    .contains(booking.getStatus().trim().toUpperCase(Locale.ROOT))) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "The booking is no longer active for price approval."));
            }
            Payment payment = paymentRepository.findByBooking_Id(id).orElse(null);
            if (payment == null || !"UNPAID".equalsIgnoreCase(payment.getStatus())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message",
                                "The price cannot change after payment has been recorded."));
            }
            proposal = bookingPricingService.refreshPendingProposal(booking, proposal);
            try {
                booking.setPrice(proposal.getProposedTotal()
                        .setScale(2, RoundingMode.HALF_UP));
            } catch (ArithmeticException exception) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "The proposed total exceeds the supported booking amount."));
            }
            payment.setAmount(booking.getPrice());
            paymentRepository.save(payment);
            bookingRepository.save(booking);
        }
        proposal.setStatus(decision);
        priceHistoryRepository.save(proposal);
        User provider = booking.getProvider();
        if (provider != null) {
            String message = "The customer " + decision.toLowerCase(Locale.ROOT)
                    + " the revised price for booking " + booking.getBookingId() + ".";
            lifecycleService.priceProposalEvent(
                    booking, customer, provider, "BOOKING_PRICE_" + decision, message);
        }
        return ResponseEntity.ok(priceHistoryView(proposal));
    }

    @GetMapping("/service-requests/{id}/price-history")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getPriceHistory(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = authenticatedUserId(jwt);
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        BookingEntity booking = bookingRepository.findById(id).orElse(null);
        if (user == null || booking == null || !canViewBooking(user, booking)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Booking was not found for this account."));
        }
        return ResponseEntity.ok(priceHistoryRepository.findByBooking_IdOrderByCreatedAtAsc(id)
                .stream().map(this::priceHistoryView).toList());
    }

    // ==========================================
    // ACCEPT REQUEST - PROVIDER
    // ==========================================

    @PutMapping("/service-requests/{id}/accept")
    @Transactional
    public ResponseEntity<?> acceptRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = authenticatedUserId(jwt);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid authentication token"));
        }

        User provider = userRepository.findById(userId)
                .orElse(null);

        if (provider == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Provider not found"));
        }

        if (!"PROVIDER".equalsIgnoreCase(provider.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "message",
                            "Only providers can accept requests"
                    ));
        }

        ProviderProfile providerProfile =
                providerProfileRepository.findByUserIdForUpdate(provider.getId())
                        .orElse(null);
        if (providerProfile == null || !provider.isProviderApproved()
                || !"APPROVED".equalsIgnoreCase(providerProfile.getVerificationStatus())
                || !providerProfile.isOnline()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message",
                            "An approved, online provider profile is required to accept requests."));
        }

        if (provider.getServiceCategory() == null
                || provider.getServiceCategory().isBlank()) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Provider service category is not set"
                    ));
        }

        // Lock the booking row to prevent two providers accepting it
        BookingEntity booking =
                bookingRepository.findByIdForUpdate(id).orElse(null);

        if (booking == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Request not found"));
        }

        if (booking.getProvider() != null
                || !"PENDING".equalsIgnoreCase(booking.getStatus())) {

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Request is no longer available"
                    ));
        }

        if (booking.getProblemImageData() != null
                && !photoViewRepository.existsByBooking_IdAndProvider_Id(id, provider.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message",
                            "Review the customer's problem photo before accepting this request."));
        }

        ProviderProfile profile = providerProfileRepository.findByUser_Id(provider.getId())
                .orElse(null);
        if (profile == null || !provider.isProviderApproved()
                || !"APPROVED".equalsIgnoreCase(profile.getVerificationStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Provider verification is required."));
        }

        if (!categoriesMatch(
                provider.getServiceCategory(),
                booking.getService())) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "message",
                            "Your service category does not match this request"
                    ));
        }

        if (!matchesProviderLocation(profile, booking)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message",
                            "This request is outside your configured service radius."));
        }

        LocalDateTime appointment = booking.getPreferredAt();
        if (appointment == null) {
            try {
                appointment = LocalDate.parse(booking.getPreferredDate())
                        .atTime(LocalTime.of(9, 0));
                booking.setPreferredAt(appointment);
            } catch (DateTimeParseException exception) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "Booking has no valid appointment time."));
            }
        }
        LocalDateTime appointmentTime = appointment;
        boolean available = availabilityRepository
                .findByProvider_IdAndDayOfWeekAndEnabledTrue(
                        providerProfile.getId(), appointmentTime.getDayOfWeek())
                .stream()
                .anyMatch(slot -> slot.getStartTime().compareTo(appointmentTime.toLocalTime()) <= 0
                        && slot.getEndTime().compareTo(
                                appointmentTime.toLocalTime().plusHours(1)) >= 0);
        if (!available) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "You are not available at this appointment time."));
        }
        if (bookingRepository.hasOverlappingAppointment(
                provider.getId(), appointment.plusHours(1), appointment.minusHours(1))) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "This appointment overlaps another assigned job."));
        }

        // Assign provider
        booking.setProvider(provider);
        booking.setProviderName(provider.getFullName());

        // Update status in database
        String previousStatus = booking.getStatus();
        booking.setStatus("ACCEPTED");

        BookingEntity savedBooking = bookingRepository.save(booking);
        lifecycleService.update(savedBooking, provider, previousStatus, "ACCEPTED",
                "Your request was accepted by " + provider.getFullName() + ".");

        return ResponseEntity.ok(addProviderDetails(savedBooking));
    }

    // ==========================================
    // REJECT REQUEST - PROVIDER
    // ==========================================

    @PutMapping("/service-requests/{id}/reject")
    @Transactional
    public ResponseEntity<?> rejectRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = authenticatedUserId(jwt);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid authentication token"));
        }

        User provider = userRepository.findById(userId)
                .orElse(null);

        if (provider == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Provider not found"));
        }

        if (!"PROVIDER".equalsIgnoreCase(provider.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "message",
                            "Only providers can reject requests"
                    ));
        }
        ProviderProfile providerProfile = providerProfileRepository
                .findByUser_Id(provider.getId()).orElse(null);
        if (!provider.isActive() || !provider.isProviderApproved()
                || providerProfile == null || !providerProfile.isOnline()
                || !"APPROVED".equalsIgnoreCase(providerProfile.getVerificationStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message",
                            "An approved, online provider profile is required to dismiss requests."));
        }

        BookingEntity booking = bookingRepository.findByIdForUpdate(id)
                .orElse(null);

        if (booking == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Request not found"));
        }

        if (booking.getProvider() != null
                || !"PENDING".equalsIgnoreCase(booking.getStatus())) {

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Request is no longer available"
                    ));
        }

        if (!categoriesMatch(
                provider.getServiceCategory(),
                booking.getService())
                || !matchesProviderLocation(providerProfile, booking)) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "message",
                            "Your service category does not match this request"
                    ));
        }

        if (!dismissalRepository.existsByBooking_IdAndProvider_Id(
                booking.getId(), provider.getId())) {
            dismissalRepository.save(new BookingDismissal(booking, provider));
        }
        return ResponseEntity.ok(Map.of("message", "Request dismissed"));
    }

    @PutMapping("/service-requests/{id}/status")
    @Transactional
    public ResponseEntity<?> updateProviderStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BookingStatusUpdateRequest request) {
        Long userId = authenticatedUserId(jwt);
        User provider = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (provider == null || !"PROVIDER".equalsIgnoreCase(provider.getRole())
                || !provider.isActive()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "An active provider account is required to update this job."));
        }
        BookingEntity booking = bookingRepository.findByIdForUpdate(id).orElse(null);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }
        if (booking.getProvider() == null
                || !booking.getProvider().getId().equals(provider.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You can only update bookings assigned to you."));
        }
        if ("IN_PROGRESS".equalsIgnoreCase(request.status())) {
            if (!Set.of("ON_THE_WAY", "ARRIVED")
                    .contains(booking.getStatus().trim().toUpperCase(Locale.ROOT))) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message",
                                "The provider must be on the way or have arrived before starting the job."));
            }
            if (booking.getCompletionPinUsedAt() != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "This job start PIN has already been used."));
            }
            if (booking.getCompletionPinAttempts() >= 5) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("message", "Too many incorrect PIN attempts. Contact the customer to reset the booking."));
            }
            if (!bookingPinService.matches(request.completionPin(), booking.getCompletionPinHash())) {
                booking.incrementCompletionPinAttempts();
                bookingRepository.save(booking);
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Incorrect job start PIN. Ask the customer to read the code from their booking."));
            }
            booking.markCompletionPinUsed();
        }
        if ("COMPLETED".equalsIgnoreCase(request.status())) {
            if (!"IN_PROGRESS".equalsIgnoreCase(booking.getStatus())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "Start the job before confirming payment and completion."));
            }
            if (priceHistoryRepository.existsByBooking_IdAndStatus(
                    booking.getId(), "PENDING_APPROVAL")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message",
                                "Wait for the customer to approve or reject the extra charge before completing the service."));
            }
            Payment payment = paymentRepository.findByBooking_IdForUpdate(booking.getId()).orElse(null);
            if (payment == null) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "Payment record is not available for this booking."));
            }
            if (!Set.of("UNPAID", "PENDING", "FAILED", "PAID")
                    .contains(normalize(payment.getStatus()))) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "This payment is not available for provider collection."));
            }
            String savedPaymentMethod = normalize(payment.getMethod());
            if ("OFFLINE".equals(savedPaymentMethod)) {
                savedPaymentMethod = "CASH";
            }
            String requestedPaymentMethod = request.paymentMethod() == null
                    ? savedPaymentMethod : normalize(request.paymentMethod());
            if (!Set.of("CASH", "UPI").contains(requestedPaymentMethod)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "This booking has an unsupported payment method."));
            }
            if ("PAID".equalsIgnoreCase(payment.getStatus())
                    && !requestedPaymentMethod.equalsIgnoreCase(savedPaymentMethod)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "A completed payment method cannot be changed."));
            }
            if ("CASH".equals(requestedPaymentMethod)) {
                BigDecimal received = request.cashReceivedAmount();
                if (received == null) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "Enter the cash amount received before completing the job."));
                }
                if (payment.getAmount() == null || received.compareTo(payment.getAmount()) < 0) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "Cash received must cover the saved payable amount of INR "
                                    + (payment.getAmount() == null ? "0.00" : payment.getAmount().toPlainString())
                                    + "."));
                }
                payment.setMethod("CASH");
                if (!"PAID".equalsIgnoreCase(payment.getStatus())) {
                    lifecycleService.recordProviderPaymentConfirmation(
                            booking, provider, payment, received);
                }
            } else {
                if (!"PAID".equalsIgnoreCase(payment.getStatus())) {
                    payment.setMethod("UPI");
                    payment.setAmountCollected(null);
                    payment.setStatus("UNPAID");
                    payment.setExternalReference(null);
                    payment.setAdminNote("Provider selected UPI; awaiting customer payment through the verified gateway.");
                    paymentRepository.save(payment);
                }
            }
        }
        if (!lifecycleService.providerTransition(
                booking, provider, request.status(), request.note())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "This booking status transition is not allowed."));
        }
        BookingEntity updatedBooking = bookingRepository.save(booking);
        addPaymentDetails(updatedBooking);
        return ResponseEntity.ok(addProviderDetails(updatedBooking));
    }

    @PutMapping("/service-requests/{id}/cancel")
    @Transactional
    public ResponseEntity<?> cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = authenticatedUserId(jwt);
        User actor = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (actor == null || !actor.isActive()
                || !Set.of("CUSTOMER", "PROVIDER").contains(normalize(actor.getRole()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "An active customer or provider account is required."));
        }
        BookingEntity booking = bookingRepository.findByIdForUpdate(id).orElse(null);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }
        boolean customerOwnsBooking = "CUSTOMER".equalsIgnoreCase(actor.getRole())
                && booking.getUser() != null
                && booking.getUser().getId().equals(actor.getId());
        boolean assignedProvider = "PROVIDER".equalsIgnoreCase(actor.getRole())
                && booking.getProvider() != null
                && booking.getProvider().getId().equals(actor.getId());
        if (!customerOwnsBooking && !assignedProvider) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Only the customer or assigned provider can cancel this booking."));
        }
        String previous = normalize(booking.getStatus());
        if (!Set.of("PENDING", "ACCEPTED", "CONFIRMED", "ON THE WAY", "ARRIVED")
                .contains(previous)
                || booking.getCompletionPinUsedAt() != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message",
                            "A booking can only be cancelled before the provider verifies the job-start PIN."));
        }
        booking.setStatus("CANCELLED");
        String actorLabel = assignedProvider ? "The assigned provider" : "The customer";
        lifecycleService.update(booking, actor, previous, "CANCELLED",
                actorLabel + " cancelled this booking before the job-start PIN was verified.");
        return ResponseEntity.ok(bookingRepository.save(booking));
    }

    @GetMapping("/service-requests/{id}/history")
    public ResponseEntity<?> statusHistory(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = authenticatedUserId(jwt);
        BookingEntity booking = bookingRepository.findById(id).orElse(null);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }
        String role = jwt == null ? "" : jwt.getClaimAsString("role");
        boolean participant = booking.getUser().getId().equals(userId)
                || (booking.getProvider() != null
                && booking.getProvider().getId().equals(userId));
        if (!participant && !"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You cannot view this booking history."));
        }
        return ResponseEntity.ok(historyRepository.findByBooking_IdOrderByChangedAtAsc(id)
                .stream().map(this::historyView).toList());
    }

    @PostMapping("/service-requests/{id}/review")
    @Transactional
    public ResponseEntity<?> submitReview(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ReviewRequest request) {
        Long userId = authenticatedUserId(jwt);
        User customer = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (customer == null || !"CUSTOMER".equalsIgnoreCase(customer.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Customer account required."));
        }
        BookingEntity booking = bookingRepository.findById(id).orElse(null);
        if (booking == null || !booking.getUser().getId().equals(customer.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Booking was not found for this account."));
        }
        if (!"COMPLETED".equalsIgnoreCase(booking.getStatus())
                || booking.getProvider() == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Only completed provider bookings can be reviewed."));
        }
        if (request.rating() < 1 || request.rating() > 5) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Rating must be between 1 and 5."));
        }
        if (reviewRepository.existsByBooking_IdAndCustomer_Id(id, customer.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "A review has already been submitted for this booking."));
        }
        Review review = new Review();
        review.setBooking(booking);
        review.setCustomer(customer);
        review.setProvider(booking.getProvider());
        review.setRating(request.rating());
        review.setComment(request.comment() == null ? "" : request.comment().trim());
        Review saved = reviewRepository.save(review);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", saved.getId(),
                "rating", saved.getRating(),
                "comment", saved.getComment()
        ));
    }

    @GetMapping("/service-requests/{id}/invoice")
    @Transactional(readOnly = true)
    public ResponseEntity<?> invoice(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = authenticatedUserId(jwt);
        BookingEntity booking = bookingRepository.findById(id).orElse(null);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }
        String role = jwt == null ? "" : jwt.getClaimAsString("role");
        if (!"ADMIN".equalsIgnoreCase(role)
                && (userId == null || !booking.getUser().getId().equals(userId))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You cannot view this invoice."));
        }
        return invoiceRepository.findByBooking_Id(id)
                .<ResponseEntity<?>>map(invoice -> {
                    InvoicePricing invoicePricing = invoicePricing(booking);
                    Payment payment = paymentRepository.findByBooking_Id(id).orElse(null);
                    return ResponseEntity.ok(Map.<String, Object>ofEntries(
                            Map.entry("invoiceNumber", invoice.getInvoiceNumber()),
                            Map.entry("bookingId", booking.getBookingId()),
                            Map.entry("originalEstimate", invoicePricing.originalEstimate() == null
                                    ? "" : invoicePricing.originalEstimate()),
                            Map.entry("approvedAdditionalCharges",
                                    invoicePricing.approvedAdditionalCharges()),
                            Map.entry("approvedChargeItems", invoicePricing.approvedChargeItems().stream()
                                    .map(item -> Map.<String, Object>of(
                                            "id", item.id(),
                                            "amount", item.amount(),
                                            "explanation", item.explanation()))
                                    .toList()),
                            Map.entry("subtotal", invoice.getSubtotal()),
                            Map.entry("discount", invoice.getDiscount()),
                            Map.entry("taxableAmount", invoice.getTaxableAmount()),
                            Map.entry("tax", invoice.getTax()),
                            Map.entry("total", invoice.getTotal()),
                            Map.entry("paymentMethod", payment == null
                                    ? "Not recorded" : payment.getMethod()),
                            Map.entry("paymentStatus", payment == null
                                    ? "Not recorded" : payment.getStatus()),
                            Map.entry("cashReceivedAmount", payment == null
                                    || payment.getAmountCollected() == null
                                    ? "" : payment.getAmountCollected()),
                            Map.entry("transactionReference", payment == null
                                    ? "" : valueOrEmpty(payment.getExternalReference())),
                            Map.entry("issuedAt", invoice.getIssuedAt())
                    ));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "Invoice is issued after the booking is completed.")));
    }

    @GetMapping("/service-requests/{id}/invoice/pdf")
    @Transactional(readOnly = true)
    public ResponseEntity<?> invoicePdf(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = authenticatedUserId(jwt);
        BookingEntity booking = bookingRepository.findById(id).orElse(null);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }
        String role = jwt == null ? "" : jwt.getClaimAsString("role");
        if (!"ADMIN".equalsIgnoreCase(role)
                && (userId == null || booking.getUser() == null
                || !booking.getUser().getId().equals(userId))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You cannot download this invoice."));
        }
        Invoice invoice = invoiceRepository.findByBooking_Id(id).orElse(null);
        if (invoice == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Invoice is issued after the booking is completed."));
        }
        InvoicePricing pricing = invoicePricing(booking);
        Payment payment = paymentRepository.findByBooking_Id(id).orElse(null);
        List<RefundLine> refunds = paymentRefundRepository
                .findAllByTransaction_Booking_IdOrderByCreatedAtDesc(id).stream()
                .map(refund -> new RefundLine(
                        refund.getAmount(),
                        refund.getStatus(),
                        valueOrEmpty(refund.getGatewayRefundId()),
                        refund.getCreatedAt()))
                .toList();
        byte[] pdf = invoicePdfService.create(
                invoice,
                pricing.originalEstimate(),
                pricing.approvedAdditionalCharges(),
                pricing.approvedChargeItems().stream()
                        .map(item -> new InvoicePdfService.ChargeLine(
                                item.amount(), item.explanation()))
                        .toList(),
                payment == null ? null : payment.getMethod(),
                payment == null ? null : payment.getStatus(),
                payment == null ? null : payment.getExternalReference(),
                payment == null ? null : payment.getAmountCollected(),
                refunds);
        String filename = invoice.getInvoiceNumber().replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(pdf);
    }

    private InvoicePricing invoicePricing(BookingEntity booking) {
        List<BookingPriceHistory> histories =
                priceHistoryRepository.findByBooking_IdOrderByCreatedAtAsc(booking.getId());
        BigDecimal originalEstimate = histories.isEmpty()
                ? null : histories.get(0).getProposedTotal();
        BigDecimal approvedAdditionalCharges = histories.stream()
                .skip(1)
                .filter(history -> "APPROVED".equalsIgnoreCase(history.getStatus()))
                .map(BookingPriceHistory::getAdditionalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ApprovedChargeItem> approvedChargeItems = histories.stream()
                .skip(1)
                .filter(history -> "APPROVED".equalsIgnoreCase(history.getStatus()))
                .map(history -> new ApprovedChargeItem(
                        history.getId(),
                        history.getAdditionalAmount(),
                        valueOrEmpty(history.getExplanation())))
                .toList();
        return new InvoicePricing(originalEstimate, approvedAdditionalCharges, approvedChargeItems);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private record InvoicePricing(
            BigDecimal originalEstimate,
            BigDecimal approvedAdditionalCharges,
            List<ApprovedChargeItem> approvedChargeItems) {
    }

    private record ApprovedChargeItem(Long id, BigDecimal amount, String explanation) {
    }

    private Map<String, Object> historyView(BookingStatusHistory history) {
        return Map.of(
                "fromStatus", history.getPreviousStatus() == null ? "" : history.getPreviousStatus(),
                "toStatus", history.getNewStatus(),
                "note", history.getNote() == null ? "" : history.getNote(),
                "changedAt", history.getChangedAt(),
                "actor", history.getActor() == null ? "" : history.getActor().getFullName()
        );
    }

        private BookingEntity addPaymentDetails(BookingEntity booking) {
                paymentRepository.findByBooking_Id(booking.getId()).ifPresent(payment -> {
                        booking.setPaymentMethod(payment.getMethod());
                        booking.setPaymentStatus(payment.getStatus());
                        booking.setPaymentReference(payment.getExternalReference());
                        booking.setMerchantUpiId(payment.getMerchantUpiId());
                                booking.setPaymentCollectedAmount(payment.getAmountCollected());
                        });
                return booking;
        }

    private BookingEntity addProviderDetails(BookingEntity booking) {
        if (booking.getProvider() == null) {
            return booking;
        }
        providerProfileRepository.findByUser_Id(booking.getProvider().getId())
                .ifPresent(profile -> {
                    booking.setProviderExperience(booking.getProvider().getExperience());
                    booking.setProviderServiceArea(booking.getProvider().getServiceArea());
                    booking.setProviderProfessionalSummary(profile.getProfessionalSummary());
                });
        return booking;
    }

    private BookingEntity addPricingDetails(BookingEntity booking) {
        priceHistoryRepository.findFirstByBooking_IdAndStatusOrderByCreatedAtDesc(
                        booking.getId(), "APPROVED")
                .ifPresent(approved -> {
                    booking.setServiceSubtotal(approved.getServiceSubtotal());
                    booking.setDiscountAmount(approved.getDiscountAmount());
                    booking.setTaxableAmount(approved.getTaxableAmount());
                    booking.setTaxAmount(approved.getTaxAmount());
                });
        priceHistoryRepository.findFirstByBooking_IdAndStatusOrderByCreatedAtDesc(
                        booking.getId(), "PENDING_APPROVAL")
                .ifPresent(proposal -> {
                    BookingPriceHistory currentProposal =
                            bookingPricingService.refreshPendingProposal(booking, proposal);
                    booking.setProposedPrice(currentProposal.getProposedTotal());
                    booking.setPriceProposalExplanation(currentProposal.getExplanation());
                    booking.setPendingPriceProposalId(currentProposal.getId());
                });
        return booking;
    }

    private boolean canViewBooking(User user, BookingEntity booking) {
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            return true;
        }
        if ("CUSTOMER".equalsIgnoreCase(user.getRole())) {
            return booking.getUser() != null && booking.getUser().getId().equals(user.getId());
        }
        return "PROVIDER".equalsIgnoreCase(user.getRole())
                && booking.getProvider() != null
                && booking.getProvider().getId().equals(user.getId());
    }

    private Map<String, Object> priceHistoryView(BookingPriceHistory history) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", history.getId());
        view.put("bookingId", history.getBooking().getId());
        view.put("actorName", history.getActor() == null ? "" : history.getActor().getFullName());
        view.put("baseAmount", history.getBaseAmount());
        view.put("experienceTier", history.getExperienceTier());
        view.put("experienceMultiplier", history.getExperienceMultiplier());
        view.put("weekendSurcharge", history.getWeekendSurcharge());
        view.put("urgentSurcharge", history.getUrgentSurcharge());
        view.put("lateNightSurcharge", history.getLateNightSurcharge());
        view.put("additionalAmount", history.getAdditionalAmount());
        view.put("serviceSubtotal", history.getServiceSubtotal());
        view.put("discountAmount", history.getDiscountAmount());
        view.put("taxableAmount", history.getTaxableAmount());
        view.put("taxAmount", history.getTaxAmount());
        view.put("discountRule", history.getDiscountRule());
        view.put("proposedTotal", history.getProposedTotal());
        view.put("status", history.getStatus());
        view.put("explanation", history.getExplanation());
        view.put("createdAt", history.getCreatedAt());
        view.put("resolvedAt", history.getResolvedAt());
        return view;
    }

    // ==========================================
    // STARTING PRICE
    // ==========================================

    private Long authenticatedUserId(Jwt jwt) {
        try {
            return jwt == null ? null : Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    // ==========================================
    // CATEGORY MATCHING
    // ==========================================

    private boolean categoriesMatch(
            String providerCategory,
            String requestedService) {

        if (providerCategory == null || requestedService == null) {
            return false;
        }

        String provider = normalize(providerCategory);
        String service = normalize(requestedService);

        return switch (provider) {

            case "PLUMBER", "PLUMBING" ->
                    service.equals("PLUMBER")
                            || service.equals("PLUMBING");

            case "ELECTRICIAN", "ELECTRICAL" ->
                    service.equals("ELECTRICIAN")
                            || service.equals("ELECTRICAL");

            case "AC REPAIR", "AIR CONDITIONING",
                 "AC SERVICE", "AC REPAIR SERVICE" ->
                    service.equals("AC REPAIR")
                            || service.equals("AIR CONDITIONING")
                            || service.equals("AC SERVICE")
                            || service.equals("AC REPAIR SERVICE");

            case "CLEANING", "HOME CLEANING" ->
                    service.equals("CLEANING")
                            || service.equals("HOME CLEANING");

            case "CARPENTER", "CARPENTRY" ->
                    service.equals("CARPENTER")
                            || service.equals("CARPENTRY");

            case "PAINTING" ->
                    service.equals("PAINTING");

            default -> provider.equals(service);
        };
    }

    private boolean matchesProviderLocation(
            ProviderProfile profile,
            BookingEntity booking) {
        if (booking.getLatitude() != null || booking.getLongitude() != null) {
            if (!DistanceCalculator.validCoordinates(
                    booking.getLatitude(), booking.getLongitude())) {
                return false;
            }
            if (DistanceCalculator.validCoordinates(
                    profile.getLatitude(), profile.getLongitude())) {
                double distance = DistanceCalculator.kilometers(
                        booking.getLatitude(), booking.getLongitude(),
                        profile.getLatitude(), profile.getLongitude());
                if (distance > Math.min(profile.getServiceRadiusKm(), maximumServiceRadiusKm)) {
                    return false;
                }
            }
        }
        return true;
    }

    private String providerRequestIssue(
            User provider,
            ProviderProfile profile,
            BookingEntity booking) {
        LocalDateTime appointment = booking.getPreferredAt();
        if (appointment == null) {
            try {
                appointment = LocalDate.parse(booking.getPreferredDate())
                        .atTime(LocalTime.of(9, 0));
            } catch (DateTimeParseException | NullPointerException exception) {
                return "INVALID_APPOINTMENT";
            }
        }

        LocalDateTime requestedAt = appointment;
        boolean available = availabilityRepository
                .findByProvider_IdAndDayOfWeekAndEnabledTrue(
                        profile.getId(), requestedAt.getDayOfWeek())
                .stream()
                .anyMatch(slot -> slot.getStartTime().compareTo(
                                requestedAt.toLocalTime()) <= 0
                        && slot.getEndTime().compareTo(
                                requestedAt.toLocalTime().plusHours(1)) >= 0);
        if (!available) {
            return "OUTSIDE_AVAILABILITY";
        }
        if (bookingRepository.hasOverlappingAppointment(
                provider.getId(), requestedAt.plusHours(1), requestedAt.minusHours(1))) {
            return "OVERLAPPING_JOB";
        }
        return null;
    }

    // ==========================================
    // NORMALIZE TEXT
    // ==========================================

    private String normalize(String value) {

        return value.trim()
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }

    private ProblemImage decodeProblemImage(String encodedImage) {
        if (encodedImage == null || encodedImage.isBlank()) {
            return null;
        }
        byte[] image;
        try {
            image = Base64.getDecoder().decode(encodedImage);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Problem photo is not valid base64 data.");
        }
        if (image.length == 0 || image.length > MAX_PROBLEM_IMAGE_BYTES) {
            throw new IllegalArgumentException("Problem photo must be between 1 byte and 3 MB.");
        }
        String contentType = detectProblemImageContentType(image);
        if (contentType == null) {
            throw new IllegalArgumentException("Upload a valid PNG, JPEG, or WebP problem photo.");
        }
        return new ProblemImage(image, contentType);
    }

    private String detectProblemImageContentType(byte[] image) {
        if (image.length >= 8
                && (image[0] & 0xff) == 0x89
                && image[1] == 'P' && image[2] == 'N' && image[3] == 'G'
                && (image[4] & 0xff) == 0x0d
                && (image[5] & 0xff) == 0x0a
                && (image[6] & 0xff) == 0x1a
                && (image[7] & 0xff) == 0x0a) {
            return "image/png";
        }
        if (image.length >= 3
                && (image[0] & 0xff) == 0xff
                && (image[1] & 0xff) == 0xd8
                && (image[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (image.length >= 12
                && image[0] == 'R' && image[1] == 'I'
                && image[2] == 'F' && image[3] == 'F'
                && image[8] == 'W' && image[9] == 'E'
                && image[10] == 'B' && image[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private record ProblemImage(byte[] data, String contentType) {
    }
}