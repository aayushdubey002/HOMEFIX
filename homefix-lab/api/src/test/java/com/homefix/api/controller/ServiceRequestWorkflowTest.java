package com.homefix.api.controller;

import com.homefix.api.dto.ServiceRequest;
import com.homefix.api.model.BookingEntity;
import com.homefix.api.model.User;
import com.homefix.api.repository.BookingDismissalRepository;
import com.homefix.api.repository.BookingRepository;
import com.homefix.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class ServiceRequestWorkflowTest {

    @Autowired
    private ServiceRequestController controller;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingDismissalRepository dismissalRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void clearDatabase() {
        dismissalRepository.deleteAll();
        bookingRepository.deleteAll();
        userRepository.deleteAll();
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
        assertEquals(300, created.getPrice());

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
    void acceptingRequestPersistsProviderAndStatusAndRejectsSecondProvider() {
        User customer = saveUser("Customer", "customer@example.com", "CUSTOMER", null);
        User firstProvider = saveUser(
                "First Plumber",
                "first@example.com",
                "PROVIDER",
                "PLUMBER"
        );
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

        ResponseEntity<?> firstResponse =
                controller.acceptRequest(
                        booking.getId(),
                        testJwt(firstProvider, "PROVIDER")
                );

        assertEquals(HttpStatus.OK, firstResponse.getStatusCode());

        BookingEntity persisted = bookingRepository.findById(booking.getId())
                .orElseThrow();
        assertEquals("ACCEPTED", persisted.getStatus());
        assertNotNull(persisted.getProvider());
        assertEquals(firstProvider.getId(), persisted.getProvider().getId());
        assertEquals("First Plumber", persisted.getProviderName());

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

    private User saveUser(
            String name,
            String email,
            String role,
            String serviceCategory
    ) {
        User user = new User(name, email, "test-password");
        user.setRole(role);
        user.setServiceCategory(serviceCategory);
        return userRepository.save(user);
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
