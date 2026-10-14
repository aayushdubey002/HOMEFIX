package com.homefix.api.controller;

import com.homefix.api.dto.DashboardStats;
import com.homefix.api.model.User;
import com.homefix.api.repository.BookingRepository;
import com.homefix.api.repository.HomeServiceRepository;
import com.homefix.api.repository.UserRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final UserRepository userRepository;
    private final HomeServiceRepository homeServiceRepository;
    private final BookingRepository bookingRepository;

    public DashboardController(
            UserRepository userRepository,
            HomeServiceRepository homeServiceRepository,
            BookingRepository bookingRepository) {

        this.userRepository = userRepository;
        this.homeServiceRepository = homeServiceRepository;
        this.bookingRepository = bookingRepository;
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<DashboardStats> getDashboardStats(
            @AuthenticationPrincipal Jwt jwt) {

        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException | NullPointerException exception) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return ResponseEntity.badRequest().build();
        }

        int totalCustomers = Math.toIntExact(
                userRepository.count()
        );

        int activeProviders = Math.toIntExact(
                homeServiceRepository.countByStatusIgnoreCase("active")
        );

        // Count bookings belonging to this customer
        int totalBookings = Math.toIntExact(
                bookingRepository.countByUser(user)
        );

        // Count this customer's pending requests
        int pendingRequests = Math.toIntExact(
                bookingRepository.countByUserAndStatusIgnoreCase(
                        user, "PENDING"
                )
        );

        DashboardStats stats = new DashboardStats(
                totalCustomers,
                activeProviders,
                totalBookings,
                pendingRequests
        );

        return ResponseEntity.ok(stats);
    }
}