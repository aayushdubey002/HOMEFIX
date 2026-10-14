package com.homefix.api.controller;

import com.homefix.api.dto.DashboardStats;
import com.homefix.api.model.User;
import com.homefix.api.repository.BookingRepository;
import com.homefix.api.repository.HomeServiceRepository;
import com.homefix.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

class HomeFixApiIntegrationTest {

    @Test
    void dashboardStatsEndpointShouldReturnExpectedMetrics() {
        UserRepository userRepository = mock(UserRepository.class);
        HomeServiceRepository homeServiceRepository = mock(HomeServiceRepository.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);

        User user = new User();
        user.setId(1L);
        user.setEmail("demo@example.com");
        user.setFullName("Demo User");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.count()).thenReturn(120L);
        when(homeServiceRepository.countByStatusIgnoreCase("active")).thenReturn(34L);
        when(bookingRepository.countByUser(user)).thenReturn(86L);
        when(bookingRepository.countByUserAndStatusIgnoreCase(user, "PENDING")).thenReturn(12L);

        DashboardController controller = new DashboardController(
                userRepository,
                homeServiceRepository,
                bookingRepository
        );

        ResponseEntity<DashboardStats> response =
                controller.getDashboardStats(testJwt(1L, "CUSTOMER"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(120, response.getBody().getTotalCustomers());
        assertEquals(34, response.getBody().getActiveProviders());
        assertEquals(86, response.getBody().getTotalBookings());
        assertEquals(12, response.getBody().getPendingRequests());
    }

    @Test
    void recentBookingsEndpointShouldReturnBookingList() {
        ServiceRequestController serviceRequestController =
                mock(ServiceRequestController.class);
        Jwt jwt = testJwt(1L, "CUSTOMER");
        ResponseEntity<?> expected =
                ResponseEntity.ok(List.of("database-backed-result"));
        doReturn(expected).when(serviceRequestController)
                .getServiceRequests(jwt);

        BookingController controller =
                new BookingController(serviceRequestController);

        assertSame(expected, controller.getRecentBookings(jwt));
    }

    private Jwt testJwt(Long userId, String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
    }
}
