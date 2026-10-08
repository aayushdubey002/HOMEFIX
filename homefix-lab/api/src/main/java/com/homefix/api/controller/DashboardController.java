package com.homefix.api.controller;

import com.homefix.api.dto.DashboardStats;
import com.homefix.api.model.User;
import com.homefix.api.repository.BookingRepository;
import com.homefix.api.repository.HomeServiceRepository;
import com.homefix.api.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
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
            @RequestParam(required = false) Long userId) {

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return ResponseEntity.badRequest().build();
        }

        int totalCustomers =
                (int) userRepository.count();

        int activeProviders =
                (int) homeServiceRepository.countByStatusIgnoreCase("active");

        // Total bookings for the current user
        int totalBookings =
                (int) bookingRepository.countByUser(user);

        // Total pending requests from database
        int pendingRequests =
                (int) bookingRepository.countByUserAndStatus(user, "PENDING");

        DashboardStats stats = new DashboardStats(
                totalCustomers,
                activeProviders,
                totalBookings,
                pendingRequests
        );

        return ResponseEntity.ok(stats);
    }
}