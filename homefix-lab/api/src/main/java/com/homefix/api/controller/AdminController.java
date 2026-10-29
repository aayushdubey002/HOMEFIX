package com.homefix.api.controller;

import com.homefix.api.dto.AdminDecisionRequest;
import com.homefix.api.dto.BookingStatusUpdateRequest;
import com.homefix.api.dto.PaymentConfigurationRequest;
import com.homefix.api.dto.PricingConfiguration;
import com.homefix.api.dto.RefundRequest;
import com.homefix.api.dto.ServiceCategoryRequest;
import com.homefix.api.dto.SupportResolutionRequest;
import com.homefix.api.model.AuditLog;
import com.homefix.api.model.BookingEntity;
import com.homefix.api.model.PaymentTransaction;
import com.homefix.api.model.PaymentRefund;
import com.homefix.api.model.ProviderSkill;
import com.homefix.api.model.ProviderProfile;
import com.homefix.api.model.ProviderVerificationDocument;
import com.homefix.api.model.ProviderVerificationDocumentType;
import com.homefix.api.model.ServiceCategory;
import com.homefix.api.model.SupportTicket;
import com.homefix.api.model.User;
import com.homefix.api.repository.AuditLogRepository;
import com.homefix.api.repository.BookingRepository;
import com.homefix.api.repository.PaymentRepository;
import com.homefix.api.repository.PaymentTransactionRepository;
import com.homefix.api.repository.PaymentRefundRepository;
import com.homefix.api.repository.ProviderProfileRepository;
import com.homefix.api.repository.ProviderSkillRepository;
import com.homefix.api.repository.ProviderVerificationDocumentRepository;
import com.homefix.api.repository.ReviewRepository;
import com.homefix.api.repository.ServiceCategoryRepository;
import com.homefix.api.repository.SupportTicketRepository;
import com.homefix.api.repository.UserRepository;
import com.homefix.api.service.BookingLifecycleService;
import com.homefix.api.service.ProviderVerificationDocumentStorage;
import com.homefix.api.service.PaymentConfigurationService;
import com.homefix.api.service.PricingConfigurationService;
import com.homefix.api.service.ServiceCategoryImages;
import com.homefix.api.service.ServiceCategoryImageStorage;
import com.homefix.api.service.PaymentOperationException;
import com.homefix.api.service.RazorpayPaymentService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Set<String> BOOKING_STATUSES = Set.of(
            "PENDING", "ACCEPTED", "CONFIRMED", "ON_THE_WAY",
            "ARRIVED", "IN_PROGRESS", "COMPLETED", "REJECTED", "CANCELLED"
    );

    private final UserRepository userRepository;
    private final ProviderProfileRepository providerRepository;
    private final ProviderSkillRepository providerSkillRepository;
    private final ProviderVerificationDocumentRepository providerDocumentRepository;
    private final ProviderVerificationDocumentStorage providerDocumentStorage;
    private final PaymentConfigurationService paymentConfigurationService;
    private final ServiceCategoryRepository categoryRepository;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final SupportTicketRepository ticketRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final AuditLogRepository auditLogRepository;
    private final BookingLifecycleService lifecycleService;
    private final PricingConfigurationService pricingConfigurationService;
    private final RazorpayPaymentService razorpayPaymentService;
    private final ServiceCategoryImageStorage categoryImageStorage;

    public AdminController(
            UserRepository userRepository,
            ProviderProfileRepository providerRepository,
            ProviderSkillRepository providerSkillRepository,
            ProviderVerificationDocumentRepository providerDocumentRepository,
            ProviderVerificationDocumentStorage providerDocumentStorage,
            PaymentConfigurationService paymentConfigurationService,
            ServiceCategoryRepository categoryRepository,
            BookingRepository bookingRepository,
            ReviewRepository reviewRepository,
            SupportTicketRepository ticketRepository,
            PaymentRepository paymentRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            PaymentRefundRepository paymentRefundRepository,
            AuditLogRepository auditLogRepository,
            BookingLifecycleService lifecycleService,
            PricingConfigurationService pricingConfigurationService,
            RazorpayPaymentService razorpayPaymentService,
            ServiceCategoryImageStorage categoryImageStorage) {
        this.userRepository = userRepository;
        this.providerRepository = providerRepository;
        this.providerSkillRepository = providerSkillRepository;
        this.providerDocumentRepository = providerDocumentRepository;
        this.providerDocumentStorage = providerDocumentStorage;
        this.paymentConfigurationService = paymentConfigurationService;
        this.categoryRepository = categoryRepository;
        this.bookingRepository = bookingRepository;
        this.reviewRepository = reviewRepository;
        this.ticketRepository = ticketRepository;
        this.paymentRepository = paymentRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentRefundRepository = paymentRefundRepository;
        this.auditLogRepository = auditLogRepository;
        this.lifecycleService = lifecycleService;
        this.pricingConfigurationService = pricingConfigurationService;
        this.razorpayPaymentService = razorpayPaymentService;
        this.categoryImageStorage = categoryImageStorage;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return Map.of(
                "customers", userRepository.countByRoleIgnoreCase("CUSTOMER"),
                "providers", userRepository.countByRoleIgnoreCase("PROVIDER"),
                "approvedProviders", userRepository.countByRoleIgnoreCaseAndProviderApprovedTrueAndActiveTrue("PROVIDER"),
                "pendingVerification", providerRepository.countByVerificationStatusIgnoreCase("PENDING"),
                "bookings", bookingRepository.count(),
                "pendingBookings", bookingRepository.countByStatusIgnoreCase("PENDING"),
                "openComplaints", ticketRepository.countByStatusIgnoreCase("OPEN"),
                "visibleReviews", reviewRepository.countByVisibleTrue(),
                "completedBookingValue", bookingRepository.sumCompletedBookingPrices()
        );
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> users() {
        return userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(user -> Map.<String, Object>of(
                        "id", user.getId(),
                        "name", user.getFullName() == null ? "" : user.getFullName(),
                        "email", user.getEmail() == null ? "" : user.getEmail(),
                        "phone", user.getPhone() == null ? "" : user.getPhone(),
                        "role", user.getRole() == null ? "UNKNOWN" : user.getRole(),
                        "active", user.isActive(),
                        "providerApproved", user.isProviderApproved(),
                        "createdAt", user.getCreatedAt() == null ? "" : user.getCreatedAt()
                )).toList();
    }

    @PutMapping("/users/{id}/status")
    @Transactional
    public ResponseEntity<?> updateUserStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AdminDecisionRequest request) {
        User admin = currentAdmin(jwt);
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        String status = normalized(request.status());
        if (!Set.of("ACTIVE", "SUSPENDED").contains(status)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "User status must be ACTIVE or SUSPENDED."));
        }
        if (user.getId().equals(admin.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "An administrator cannot suspend their own account."));
        }
        user.setActive("ACTIVE".equals(status));
        if (!user.isActive() && "PROVIDER".equalsIgnoreCase(user.getRole())) {
            user.setProviderApproved(false);
            providerRepository.findByUser_Id(user.getId()).ifPresent(profile -> {
                profile.setOnline(false);
                profile.setVerificationStatus("SUSPENDED");
                providerRepository.save(profile);
            });
        }
        userRepository.save(user);
        audit(admin, "USER_STATUS_CHANGED", "USER", id, status);
        return ResponseEntity.ok(Map.of("message", "User status updated.", "status", status));
    }

    @GetMapping("/providers")
        @Transactional(readOnly = true)
    public List<Map<String, Object>> providers() {
        return providerRepository.findAll().stream().map(profile -> {
            User user = profile.getUser();
            return Map.<String, Object>of(
                    "id", user.getId(),
                    "name", user.getFullName(),
                    "email", user.getEmail(),
                    "phone", user.getPhone() == null ? "" : user.getPhone(),
                    "serviceCategory", user.getServiceCategory() == null
                            ? "" : user.getServiceCategory(),
                    "experience", user.getExperience() == null ? "" : user.getExperience(),
                    "serviceArea", user.getServiceArea() == null ? "" : user.getServiceArea(),
                    "status", profile.getVerificationStatus(),
                    "documentsReady", requiredDocumentsApproved(profile),
                    "online", profile.isOnline()
            );
        }).toList();
    }

    @GetMapping("/provider-documents")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> providerDocuments(@AuthenticationPrincipal Jwt jwt) {
        currentAdmin(jwt);
        return providerDocumentRepository.findAllByOrderByUploadedAtDescIdDesc().stream()
                .map(document -> {
                    Map<String, Object> view = new java.util.LinkedHashMap<>(
                            ProviderVerificationDocumentController.documentView(document));
                    view.put("providerId", document.getProvider().getUser().getId());
                    view.put("providerName", document.getProvider().getUser().getFullName());
                    view.put("providerEmail", document.getProvider().getUser().getEmail());
                    return view;
                }).toList();
    }

    @GetMapping("/provider-documents/{documentId}/file")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> providerDocumentFile(
            @PathVariable Long documentId,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        currentAdmin(jwt);
        ProviderVerificationDocument document = providerDocumentRepository.findById(documentId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Provider document was not found."));
        Path path = providerDocumentStorage.resolve(document.getStorageKey());
        FileSystemResource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .contentLength(document.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(document.getOriginalFilename(), StandardCharsets.UTF_8)
                                .build().toString())
                .body(resource);
    }

    @PutMapping("/provider-documents/{documentId}/review")
    @Transactional
    public ResponseEntity<?> reviewProviderDocument(
            @PathVariable Long documentId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AdminDecisionRequest request) {
        User admin = currentAdmin(jwt);
        ProviderVerificationDocument document = providerDocumentRepository.findById(documentId)
                .orElse(null);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }
        String status = normalized(request.status());
        if (!Set.of("APPROVED", "REJECTED").contains(status)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Document decision must be APPROVED or REJECTED."));
        }
        if ("REJECTED".equals(status)
                && (request.note() == null || request.note().isBlank())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Provide a reason when rejecting a document."));
        }
        document.setStatus(status);
        document.setReviewNote(request.note() == null ? "" : request.note().trim());
        providerDocumentRepository.save(document);
        audit(admin, "PROVIDER_DOCUMENT_" + status, "PROVIDER_DOCUMENT",
                documentId, document.getDocumentType().name());
        return ResponseEntity.ok(Map.of(
                "message", "Provider document review saved.",
                "status", status
        ));
    }

    @PostMapping("/providers/{userId}/approval")
    @Transactional
    public ResponseEntity<?> providerApproval(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AdminDecisionRequest request) {
        User admin = currentAdmin(jwt);
        User user = userRepository.findById(userId).orElse(null);
        ProviderProfile profile = providerRepository.findByUser_Id(userId).orElse(null);
        if (user == null || profile == null
                || !"PROVIDER".equalsIgnoreCase(user.getRole())) {
            return ResponseEntity.notFound().build();
        }
        String status = normalized(request.status());
        if (!Set.of("APPROVED", "REJECTED").contains(status)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Provider decision must be APPROVED or REJECTED."));
        }
        if ("APPROVED".equals(status) && !requiredDocumentsApproved(profile)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message",
                            "Approve the provider's identity and service address documents before approving the profile."));
        }
        profile.setVerificationStatus(status);
        user.setProviderApproved("APPROVED".equals(status));
        if (!user.isProviderApproved()) {
            profile.setOnline(false);
        }
        providerRepository.save(profile);
        userRepository.save(user);
        audit(admin, "PROVIDER_" + status, "USER", userId,
                request.note() == null ? "" : request.note().trim());
        return ResponseEntity.ok(Map.of(
                "message", "Provider verification updated.",
                "status", status
        ));
    }

    private boolean requiredDocumentsApproved(ProviderProfile profile) {
        return documentApproved(profile, ProviderVerificationDocumentType.IDENTITY_PROOF)
                && documentApproved(profile, ProviderVerificationDocumentType.SERVICE_ADDRESS_PROOF);
    }

    private boolean documentApproved(
            ProviderProfile profile,
            ProviderVerificationDocumentType documentType) {
        return providerDocumentRepository
                .findFirstByProvider_IdAndDocumentTypeOrderByUploadedAtDescIdDesc(
                        profile.getId(), documentType)
                .map(document -> "APPROVED".equalsIgnoreCase(document.getStatus()))
                .orElse(false);
    }

    @GetMapping("/categories")
    public List<ServiceCategory> categories() {
        return categoryRepository.findAll();
    }

    @GetMapping("/pricing-config")
    public ResponseEntity<?> pricingConfiguration(@AuthenticationPrincipal Jwt jwt) {
        currentAdmin(jwt);
        return ResponseEntity.ok(pricingConfigurationService.getConfiguration());
    }

    @PutMapping("/pricing-config")
    @Transactional
    public ResponseEntity<?> updatePricingConfiguration(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PricingConfiguration request) {
        User admin = currentAdmin(jwt);
        PricingConfiguration saved = pricingConfigurationService.updateConfiguration(request);
        auditLogRepository.save(new AuditLog(
                admin, "PRICING_CONFIGURATION_UPDATED", "PRICING", "global",
                "Administrator updated provider experience multipliers and surcharge policy."));
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/categories")
    @Transactional
    public ResponseEntity<?> createCategory(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ServiceCategoryRequest request) {
        return createCategory(jwt, request, null);
    }

    @PostMapping(value = "/categories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> createCategoryWithImage(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestPart("category") ServiceCategoryRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return createCategory(jwt, request, image);
    }

    private ResponseEntity<?> createCategory(
            Jwt jwt,
            ServiceCategoryRequest request,
            MultipartFile image) {
        User admin = currentAdmin(jwt);
        String slug = request.slug().trim().toLowerCase(Locale.ROOT);
        String code = request.serviceCode().trim().toUpperCase(Locale.ROOT);
        if (request.startingPrice() == null || request.startingPrice().signum() < 0
                || request.startingPrice().scale() > 2) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Starting price must be a non-negative amount with at most two decimals."));
        }
        String imageKey = ServiceCategoryImages.resolve(
                request.imageKey(), request.name(), request.slug(), request.serviceCode());
        if (!ServiceCategoryImages.supports(imageKey)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Choose a service image from the HomeFix image library."));
        }
        if (categoryRepository.existsBySlugIgnoreCase(slug)
                || categoryRepository.existsByServiceCodeIgnoreCase(code)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "A category with this slug or service code already exists."));
        }
        ServiceCategory category = new ServiceCategory(
                request.name().trim(),
                slug,
                code,
                request.description() == null ? "" : request.description().trim(),
                request.startingPrice()
        );
        category.setImageKey(imageKey);
        ServiceCategoryImageStorage.StoredImage storedImage = null;
        if (image != null && !image.isEmpty()) {
            try {
                storedImage = categoryImageStorage.store(image);
                category.setImageUrl(storedImage.imageUrl());
            } catch (IOException error) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Unable to save the service image.", error);
            }
        } else if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Upload service images through the HomeFix image picker.");
        }
        category.setActive(request.active());
        try {
            ServiceCategory saved = categoryRepository.save(category);
            audit(admin, "SERVICE_CATEGORY_CREATED", "SERVICE_CATEGORY", saved.getId(), slug);
            deleteOnRollback(storedImage);
            return ResponseEntity.status(HttpStatus.CREATED).body(categoryView(saved));
        } catch (RuntimeException error) {
            deleteStoredImage(storedImage == null ? null : storedImage.storageKey());
            throw error;
        }
    }

    @PutMapping("/categories/{id}")
    @Transactional
    public ResponseEntity<?> updateCategory(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ServiceCategoryRequest request) {
        return updateCategory(id, jwt, request, null);
    }

    @PutMapping(value = "/categories/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> updateCategoryWithImage(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestPart("category") ServiceCategoryRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return updateCategory(id, jwt, request, image);
    }

    private ResponseEntity<?> updateCategory(
            Long id,
            Jwt jwt,
            ServiceCategoryRequest request,
            MultipartFile image) {
        User admin = currentAdmin(jwt);
        ServiceCategory category = categoryRepository.findById(id).orElse(null);
        if (category == null) {
            return ResponseEntity.notFound().build();
        }
        if (request.startingPrice() == null || request.startingPrice().signum() < 0
                || request.startingPrice().scale() > 2) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Starting price must be a non-negative amount with at most two decimals."));
        }
        String imageKey = ServiceCategoryImages.resolve(
                request.imageKey(), request.name(), request.slug(), request.serviceCode());
        if (!ServiceCategoryImages.supports(imageKey)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Choose a service image from the HomeFix image library."));
        }
        String slug = request.slug().trim().toLowerCase(Locale.ROOT);
        String code = request.serviceCode().trim().toUpperCase(Locale.ROOT);
        if (categoryRepository.findBySlugIgnoreCase(slug)
                .filter(existing -> !existing.getId().equals(id)).isPresent()
                || categoryRepository.existsByServiceCodeIgnoreCase(code)
                && !category.getServiceCode().equalsIgnoreCase(code)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Another category already uses this slug or service code."));
        }
        String oldImageKey = categoryImageStorage.storageKeyFromUrl(category.getImageUrl());
        ServiceCategoryImageStorage.StoredImage storedImage = null;
        if (image != null && !image.isEmpty()) {
            try {
                storedImage = categoryImageStorage.store(image);
            } catch (IOException error) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Unable to save the service image.", error);
            }
        } else if (request.imageUrl() != null) {
            if (request.imageUrl().isBlank()) {
                category.setImageUrl(null);
            } else if (!request.imageUrl().equals(category.getImageUrl())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Upload service images through the HomeFix image picker.");
            }
        }
        category.setName(request.name().trim());
        category.setSlug(slug);
        category.setServiceCode(code);
        category.setDescription(request.description() == null ? "" : request.description().trim());
        category.setImageKey(imageKey);
        if (storedImage != null) {
            category.setImageUrl(storedImage.imageUrl());
        }
        boolean startingPriceChanged = category.getStartingPrice()
                .compareTo(request.startingPrice()) != 0;
        category.setStartingPrice(request.startingPrice());
        category.setActive(request.active());
        try {
            categoryRepository.save(category);
            if (startingPriceChanged) {
                List<ProviderSkill> providerSkills =
                        providerSkillRepository.findByCategory_Id(category.getId());
                providerSkills.forEach(skill -> skill.setPrice(category.getStartingPrice()));
                providerSkillRepository.saveAll(providerSkills);
            }
            audit(admin, "SERVICE_CATEGORY_UPDATED", "SERVICE_CATEGORY", id, slug);
            if (storedImage != null) {
                deleteOnRollback(storedImage);
            }
            if (oldImageKey != null
                    && (storedImage != null
                    || request.imageUrl() != null && request.imageUrl().isBlank())) {
                deleteAfterCommit(oldImageKey);
            }
            return ResponseEntity.ok(categoryView(category));
        } catch (RuntimeException error) {
            deleteStoredImage(storedImage == null ? null : storedImage.storageKey());
            throw error;
        }
    }

    @GetMapping("/bookings")
    public List<Map<String, Object>> bookings() {
        return bookingRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::bookingView).toList();
    }

    @PutMapping("/bookings/{id}/status")
    @Transactional
    public ResponseEntity<?> updateBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BookingStatusUpdateRequest request) {
        User admin = currentAdmin(jwt);
        BookingEntity booking = bookingRepository.findByIdForUpdate(id).orElse(null);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }
        String status = normalized(request.status());
        if (!BOOKING_STATUSES.contains(status)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Select a recognized booking status."));
        }
        String previous = normalized(booking.getStatus());
        if (previous.equals(status)) {
            return ResponseEntity.ok(bookingView(booking));
        }
        booking.setStatus(status);
        lifecycleService.update(booking, admin, previous, status, request.note());
        bookingRepository.save(booking);
        return ResponseEntity.ok(bookingView(booking));
    }

    @GetMapping("/tickets")
    public List<Map<String, Object>> tickets() {
        return ticketRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::ticketView).toList();
    }

    @PutMapping("/tickets/{id}")
    @Transactional
    public ResponseEntity<?> updateTicket(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SupportResolutionRequest request) {
        User admin = currentAdmin(jwt);
        SupportTicket ticket = ticketRepository.findById(id).orElse(null);
        if (ticket == null) {
            return ResponseEntity.notFound().build();
        }
        String status = normalized(request.status());
        if (!Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED").contains(status)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Select OPEN, IN_PROGRESS, RESOLVED or CLOSED."));
        }
        ticket.setStatus(status);
        ticket.setResolution(request.resolution() == null ? "" : request.resolution().trim());
        ticket.setAssignedAdmin(admin);
        ticketRepository.save(ticket);
        audit(admin, "SUPPORT_TICKET_UPDATED", "SUPPORT_TICKET", id, status);
        return ResponseEntity.ok(ticketView(ticket));
    }

    @GetMapping("/reviews")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> reviews() {
        return reviewRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(review -> Map.<String, Object>of(
                        "id", review.getId(),
                        "bookingId", review.getBooking().getBookingId(),
                        "customerName", review.getCustomer().getFullName(),
                        "providerName", review.getProvider().getFullName(),
                        "rating", review.getRating(),
                        "comment", review.getComment() == null ? "" : review.getComment(),
                        "visible", review.isVisible(),
                        "createdAt", review.getCreatedAt()
                )).toList();
    }

    @PutMapping("/reviews/{id}/visibility")
    @Transactional
    public ResponseEntity<?> updateReviewVisibility(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AdminDecisionRequest request) {
        User admin = currentAdmin(jwt);
        var review = reviewRepository.findById(id).orElse(null);
        if (review == null) {
            return ResponseEntity.notFound().build();
        }
        String status = normalized(request.status());
        if (!Set.of("VISIBLE", "HIDDEN").contains(status)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Review visibility must be VISIBLE or HIDDEN."));
        }
        review.setVisible("VISIBLE".equals(status));
        reviewRepository.save(review);
        audit(admin, "REVIEW_VISIBILITY_CHANGED", "REVIEW", id, status);
        return ResponseEntity.ok(Map.of("message", "Review visibility updated."));
    }

    @GetMapping("/payments")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> payments() {
        Map<Long, List<PaymentTransaction>> transactionsByBooking =
                paymentTransactionRepository.findAllByOrderByCreatedAtDesc().stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                transaction -> transaction.getBooking().getId()));
        Map<Long, List<PaymentRefund>> refundsByTransaction =
                paymentRefundRepository.findAllByOrderByCreatedAtDesc().stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                refund -> refund.getTransaction().getId()));
        return paymentRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(payment -> {
                    List<Map<String, Object>> attempts = transactionsByBooking
                            .getOrDefault(payment.getBooking().getId(), List.of()).stream()
                            .map(transaction -> Map.<String, Object>ofEntries(
                                    Map.entry("transactionId", transaction.getId()),
                                    Map.entry("gatewayOrderId", valueOrEmpty(transaction.getGatewayOrderId())),
                                    Map.entry("gatewayPaymentId", valueOrEmpty(transaction.getGatewayPaymentId())),
                                    Map.entry("amount", transaction.getAmount()),
                                    Map.entry("currency", transaction.getCurrency()),
                                    Map.entry("status", transaction.getStatus()),
                                    Map.entry("failureReason", valueOrEmpty(transaction.getFailureReason())),
                                    Map.entry("createdAt", transaction.getCreatedAt()),
                                    Map.entry("updatedAt", transaction.getUpdatedAt()),
                                    Map.entry("refunds", refundsByTransaction
                                            .getOrDefault(transaction.getId(), List.of()).stream()
                                            .map(this::refundView).toList())
                            )).toList();
                    return Map.<String, Object>ofEntries(
                            Map.entry("id", payment.getId()),
                            Map.entry("bookingId", payment.getBooking().getBookingId()),
                            Map.entry("customer", payment.getBooking().getCustomerName()),
                            Map.entry("provider", payment.getBooking().getProviderName() == null
                                    ? "Not assigned" : payment.getBooking().getProviderName()),
                            Map.entry("service", payment.getBooking().getService()),
                            Map.entry("amount", payment.getAmount()),
                            Map.entry("cashReceivedAmount", payment.getAmountCollected() == null
                                    ? "" : payment.getAmountCollected()),
                            Map.entry("status", payment.getStatus()),
                            Map.entry("method", payment.getMethod()),
                            Map.entry("reference", payment.getExternalReference() == null
                                    ? "" : payment.getExternalReference()),
                            Map.entry("note", payment.getAdminNote() == null
                                    ? "" : payment.getAdminNote()),
                            Map.entry("updatedAt", payment.getUpdatedAt()),
                            Map.entry("transactions", attempts)
                    );
                }).toList();
    }

    @PostMapping("/payments/transactions/{transactionId}/refunds")
    @Transactional
    public ResponseEntity<?> requestRefund(
            @PathVariable Long transactionId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RefundRequest request) {
        User admin = currentAdmin(jwt);
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{8,80}")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "A valid idempotency key is required."));
        }
        try {
            PaymentRefund refund = razorpayPaymentService.requestRefund(
                    transactionId, admin, idempotencyKey, request.amount(), request.reason());
            return ResponseEntity.ok(refundView(refund));
        } catch (PaymentOperationException exception) {
            return ResponseEntity.status(exception.getStatus())
                    .body(Map.of("message", exception.getMessage()));
        }
    }

    private Map<String, Object> refundView(PaymentRefund refund) {
        return Map.ofEntries(
                Map.entry("refundId", refund.getId()),
                Map.entry("transactionId", refund.getTransaction().getId()),
                Map.entry("amount", refund.getAmount()),
                Map.entry("reason", refund.getReason()),
                Map.entry("status", refund.getStatus()),
                Map.entry("gatewayRefundId", valueOrEmpty(refund.getGatewayRefundId())),
                Map.entry("failureReason", valueOrEmpty(refund.getFailureReason())),
                Map.entry("requestedBy", refund.getRequestedByAdmin().getFullName()),
                Map.entry("createdAt", refund.getCreatedAt()),
                Map.entry("updatedAt", refund.getUpdatedAt())
        );
    }

    @PutMapping("/payment-config")
    @Transactional
    public ResponseEntity<?> updatePaymentConfiguration(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PaymentConfigurationRequest request) {
        User admin = currentAdmin(jwt);
        String upiId = paymentConfigurationService.updateUpiId(request.upiId());
        audit(admin, "PAYMENT_UPI_ID_UPDATED", "PLATFORM_SETTING", null,
                "HomeFix UPI ID was updated.");
        return ResponseEntity.ok(Map.of(
                "upiEnabled", true,
                "upiId", upiId,
                "message", "HomeFix UPI ID saved. New bookings will use this account."
        ));
    }

    @GetMapping("/reports/bookings.csv")
    public ResponseEntity<byte[]> exportBookings() {
        StringBuilder csv = new StringBuilder(
                "booking_id,customer,service,provider,date,time,status,price\n");
        for (BookingEntity booking : bookingRepository.findAllByOrderByCreatedAtDesc()) {
            csv.append(csv(booking.getBookingId())).append(',')
                    .append(csv(booking.getCustomerName())).append(',')
                    .append(csv(booking.getService())).append(',')
                    .append(csv(booking.getProviderName())).append(',')
                    .append(csv(booking.getPreferredDate())).append(',')
                    .append(csv(booking.getPreferredAt() == null ? ""
                            : booking.getPreferredAt().toLocalTime().toString())).append(',')
                    .append(csv(booking.getStatus())).append(',')
                    .append(booking.getPrice() == null ? "" : booking.getPrice())
                    .append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=homefix-bookings.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> audit() {
        return auditLogRepository.findTop200ByOrderByOccurredAtDesc().stream()
                .map(this::auditView).toList();
    }

    private Map<String, Object> bookingView(BookingEntity booking) {
        return Map.of(
                "id", booking.getId(),
                "bookingId", booking.getBookingId(),
                "customerName", booking.getCustomerName(),
                "service", booking.getService(),
                "providerName", booking.getProviderName() == null ? "" : booking.getProviderName(),
                "preferredDate", booking.getPreferredDate() == null ? "" : booking.getPreferredDate(),
                "preferredTime", booking.getPreferredAt() == null ? ""
                        : booking.getPreferredAt().toLocalTime().toString(),
                "status", booking.getStatus(),
                "price", booking.getPrice() == null ? 0 : booking.getPrice()
        );
    }

    private Map<String, Object> ticketView(SupportTicket ticket) {
        return Map.of(
                "id", ticket.getId(),
                "customerName", ticket.getCustomer().getFullName(),
                "subject", ticket.getSubject(),
                "description", ticket.getDescription(),
                "status", ticket.getStatus(),
                "resolution", ticket.getResolution() == null ? "" : ticket.getResolution(),
                "bookingId", ticket.getBooking() == null ? "" : ticket.getBooking().getBookingId()
        );
    }

    private Map<String, Object> categoryView(ServiceCategory category) {
        return Map.of(
                "id", category.getId(),
                "name", category.getName(),
                "slug", category.getSlug(),
                "serviceCode", category.getServiceCode(),
                "description", category.getDescription() == null ? "" : category.getDescription(),
                "imageKey", ServiceCategoryImages.resolve(
                        category.getImageKey(),
                        category.getName(),
                        category.getSlug(),
                        category.getServiceCode()),
                "imageUrl", category.getImageUrl() == null ? "" : category.getImageUrl(),
                "startingPrice", category.getStartingPrice(),
                "active", category.isActive()
        );
    }

    private void deleteOnRollback(ServiceCategoryImageStorage.StoredImage storedImage) {
        if (storedImage == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteStoredImage(storedImage.storageKey());
                }
            }
        });
    }

    private void deleteAfterCommit(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteStoredImage(storageKey);
            }
        });
    }

    private void deleteStoredImage(String storageKey) {
        if (storageKey == null) {
            return;
        }
        try {
            categoryImageStorage.delete(storageKey);
        } catch (IOException error) {
            org.slf4j.LoggerFactory.getLogger(AdminController.class)
                    .warn("Unable to clean up replaced service image {}", storageKey, error);
        }
    }

    private Map<String, Object> auditView(AuditLog log) {
        return Map.of(
                "id", log.getId(),
                "actor", log.getActor() == null ? "system" : log.getActor().getFullName(),
                "action", log.getAction(),
                "entityType", log.getEntityType(),
                "entityId", log.getEntityId() == null ? "" : log.getEntityId(),
                "details", log.getDetails() == null ? "" : log.getDetails(),
                "occurredAt", log.getOccurredAt()
        );
    }

    private void audit(User actor, String action, String entityType, Long entityId,
                       String details) {
        auditLogRepository.save(new AuditLog(
                actor, action, entityType, String.valueOf(entityId), details));
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String safe = value.replace("\r", " ").replace("\n", " ");
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private User currentAdmin(Jwt jwt) {
        try {
            return userRepository.findById(Long.valueOf(jwt.getSubject()))
                    .filter(user -> user.isActive()
                            && "ADMIN".equalsIgnoreCase(user.getRole()))
                    .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                            "Active administrator account required."));
        } catch (NumberFormatException | NullPointerException exception) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Active administrator account required.");
        }
    }
}
