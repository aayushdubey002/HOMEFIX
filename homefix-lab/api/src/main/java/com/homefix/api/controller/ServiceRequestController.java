package com.homefix.api.controller;

import com.homefix.api.dto.ServiceRequest;
import com.homefix.api.model.BookingDismissal;
import com.homefix.api.model.BookingEntity;
import com.homefix.api.model.User;
import com.homefix.api.repository.BookingRepository;
import com.homefix.api.repository.BookingDismissalRepository;
import com.homefix.api.repository.UserRepository;
import com.homefix.api.service.ServiceCatalog;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class ServiceRequestController {

    private final BookingRepository bookingRepository;
    private final BookingDismissalRepository dismissalRepository;
    private final UserRepository userRepository;

    public ServiceRequestController(
            BookingRepository bookingRepository,
            BookingDismissalRepository dismissalRepository,
            UserRepository userRepository) {

        this.bookingRepository = bookingRepository;
        this.dismissalRepository = dismissalRepository;
        this.userRepository = userRepository;
    }

    // ==========================================
    // CREATE SERVICE REQUEST - CUSTOMER
    // ==========================================

    @PostMapping("/service-requests")
    public ResponseEntity<?> createServiceRequest(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ServiceRequest request) {

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

        String service = normalize(request.getService());

        Integer price = ServiceCatalog.startingPrice(service);

        if (price == null) {
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

        booking.setPhone(request.getPhone().trim());
        booking.setPrice(price);
        booking.setAddress(request.getAddress().trim());
        booking.setPreferredDate(request.getPreferredDate());
        booking.setNotes(request.getNotes());

        BookingEntity savedBooking = bookingRepository.save(booking);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedBooking);
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

            List<BookingEntity> bookings =
                    bookingRepository.findByUserOrderByIdDesc(customer);

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

            if (category == null || category.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "message",
                                "Provider service category is not set"
                        ));
            }

            List<BookingEntity> pendingRequests =
                    bookingRepository
                            .findByProviderIsNullAndStatusIgnoreCaseOrderByIdDesc(
                                    "PENDING"
                            );

            List<BookingEntity> assignedBookings =
                    bookingRepository.findByProviderOrderByIdDesc(provider);

            Set<Long> dismissedBookingIds = dismissalRepository
                    .findByProvider_Id(provider.getId())
                    .stream()
                    .map(dismissal -> dismissal.getBooking().getId())
                    .collect(java.util.stream.Collectors.toSet());

            LinkedHashMap<Long, BookingEntity> result =
                    new LinkedHashMap<>();

            // Show only matching category's pending requests
            for (BookingEntity booking : pendingRequests) {

                if (categoriesMatch(
                        category,
                        booking.getService())
                        && !dismissedBookingIds.contains(booking.getId())) {

                    result.put(booking.getId(), booking);
                }
            }

            // Also show bookings already assigned to this provider
            for (BookingEntity booking : assignedBookings) {
                result.put(booking.getId(), booking);
            }

            return ResponseEntity.ok(
                    result.values().stream().toList()
            );
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "message", "Account role is not supported"
                ));
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

        if (!categoriesMatch(
                provider.getServiceCategory(),
                booking.getService())) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "message",
                            "Your service category does not match this request"
                    ));
        }

        // Assign provider
        booking.setProvider(provider);
        booking.setProviderName(provider.getFullName());

        // Update status in database
        booking.setStatus("ACCEPTED");

        BookingEntity savedBooking = bookingRepository.save(booking);

        return ResponseEntity.ok(savedBooking);
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
                booking.getService())) {

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
}