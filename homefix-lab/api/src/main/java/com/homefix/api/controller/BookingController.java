package com.homefix.api.controller;

import com.homefix.api.dto.ServiceRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BookingController {

    private final ServiceRequestController serviceRequestController;

    public BookingController(ServiceRequestController serviceRequestController) {
        this.serviceRequestController = serviceRequestController;
    }

    @GetMapping("/bookings/recent")
    public ResponseEntity<?> getRecentBookings(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return serviceRequestController.getServiceRequests(jwt);
    }

    @PostMapping("/bookings/create")
    public ResponseEntity<?> createBooking(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ServiceRequest request
    ) {
        return serviceRequestController.createServiceRequest(jwt, request);
    }
}
