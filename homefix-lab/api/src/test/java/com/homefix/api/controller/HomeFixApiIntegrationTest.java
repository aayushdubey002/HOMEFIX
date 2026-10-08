package com.homefix.api.controller;

import com.homefix.api.dto.Booking;
import com.homefix.api.dto.DashboardStats;
import com.homefix.api.model.User;
import com.homefix.api.repository.BookingRepository;
import com.homefix.api.repository.HomeServiceRepository;
import com.homefix.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
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
        when(bookingRepository.countByUserAndStatus(user, "PENDING")).thenReturn(12L);

        DashboardController controller = new DashboardController(
                userRepository,
                homeServiceRepository,
                bookingRepository
        );

        ResponseEntity<DashboardStats> response = controller.getDashboardStats(1L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(120, response.getBody().getTotalCustomers());
        assertEquals(34, response.getBody().getActiveProviders());
        assertEquals(86, response.getBody().getTotalBookings());
        assertEquals(12, response.getBody().getPendingRequests());
    }

    @Test
    void recentBookingsEndpointShouldReturnBookingList() {
        BookingController controller = new BookingController();
        List<Booking> bookings = controller.getRecentBookings();

        assertNotNull(bookings);
        assertFalse(bookings.isEmpty());
        assertNotNull(bookings.get(0).getCustomerName());
        assertNotNull(bookings.get(0).getService());
        assertNotNull(bookings.get(0).getStatus());
    }
}
