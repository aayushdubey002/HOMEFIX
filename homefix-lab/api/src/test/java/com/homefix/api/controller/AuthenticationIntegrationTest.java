package com.homefix.api.controller;

import com.homefix.api.model.BookingEntity;
import com.homefix.api.model.User;
import com.homefix.api.repository.BookingDismissalRepository;
import com.homefix.api.repository.BookingRepository;
import com.homefix.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationIntegrationTest {

    @Value("${local.server.port}")
    private int serverPort;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingDismissalRepository dismissalRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearDatabase() {
        dismissalRepository.deleteAll();
        bookingRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void customerRegistrationLoginAndBookingUseAuthenticatedDatabaseIdentity()
            throws Exception {
        String email = "customer-" + UUID.randomUUID() + "@example.test";
        String password = UUID.randomUUID().toString();
        HttpResponse<String> registerResponse = send(
                "POST",
                "/api/auth/register",
                null,
                "{\"fullName\":\"Integration Customer\",\"email\":\""
                        + email + "\",\"password\":\"" + password
                        + "\",\"role\":\"CUSTOMER\",\"phone\":\"9876543210\"}"
        );
        assertEquals(201, registerResponse.statusCode());

        User registered = userRepository.findByEmail(email).orElseThrow();
        assertEquals("9876543210", registered.getPhone());
        assertTrue(passwordEncoder.matches(password, registered.getPassword()));

        HttpResponse<String> unauthorizedResponse = send(
                "GET", "/api/service-requests", null, null
        );
        assertEquals(401, unauthorizedResponse.statusCode());

        HttpResponse<String> loginResponse = send(
                "POST",
                "/api/auth/login",
                null,
                "{\"email\":\"" + email + "\",\"password\":\""
                        + password + "\",\"role\":\"CUSTOMER\"}"
        );
        assertEquals(200, loginResponse.statusCode());
        assertFalse(loginResponse.body().contains("password"));
        assertTrue(loginResponse.body().contains("\"id\":" + registered.getId()));
        assertTrue(loginResponse.body().contains("\"phone\":\"9876543210\""));
        Matcher tokenMatcher = Pattern.compile("\"accessToken\":\"([^\"]+)\"")
                .matcher(loginResponse.body());
        assertTrue(tokenMatcher.find());
        String token = tokenMatcher.group(1);

        HttpResponse<String> createResponse = send(
                "POST",
                "/api/service-requests?userId=999999",
                token,
                "{\"customerName\":\"Integration Customer\","
                        + "\"service\":\"PLUMBING\",\"phone\":\"9876543210\","
                        + "\"address\":\"10 Main Street\",\"preferredDate\":\""
                        + LocalDate.now().plusDays(1)
                        + "\",\"notes\":\"Leaking kitchen tap\"}"
        );
        assertEquals(201, createResponse.statusCode());
        assertTrue(createResponse.body().contains("\"price\":300"));
        assertTrue(createResponse.body().contains("\"id\":" + registered.getId()));
        assertFalse(createResponse.body().contains("password"));

        BookingEntity savedBooking = bookingRepository.findAll().get(0);
        assertEquals(registered.getId(), savedBooking.getUser().getId());
        assertEquals(300, savedBooking.getPrice());
        assertEquals("PENDING", savedBooking.getStatus());

        HttpResponse<String> listResponse = send(
                "GET", "/api/service-requests?userId=999999", token, null
        );
        assertEquals(200, listResponse.statusCode());
        assertTrue(listResponse.body().contains(savedBooking.getBookingId()));

        String firstProviderToken = registerAndLoginProvider("First Plumber");
        HttpResponse<String> providerRequests = send(
                "GET", "/api/service-requests?providerId=999999", firstProviderToken, null
        );
        assertEquals(200, providerRequests.statusCode());
        assertTrue(providerRequests.body().contains(savedBooking.getBookingId()));

        HttpResponse<String> acceptResponse = send(
                "PUT",
                "/api/service-requests/" + savedBooking.getId() + "/accept",
                firstProviderToken,
                null
        );
        assertEquals(200, acceptResponse.statusCode());
        assertTrue(acceptResponse.body().contains("\"status\":\"ACCEPTED\""));
        BookingEntity acceptedBooking = bookingRepository.findById(savedBooking.getId())
                .orElseThrow();
        assertEquals("ACCEPTED", acceptedBooking.getStatus());
        assertEquals("First Plumber", acceptedBooking.getProvider().getFullName());

        String secondProviderToken = registerAndLoginProvider("Second Plumber");
        HttpResponse<String> secondAcceptResponse = send(
                "PUT",
                "/api/service-requests/" + savedBooking.getId() + "/accept",
                secondProviderToken,
                null
        );
        assertEquals(409, secondAcceptResponse.statusCode());

        HttpResponse<String> refreshedCustomerBookings = send(
                "GET", "/api/service-requests", token, null
        );
        assertTrue(refreshedCustomerBookings.body().contains("\"status\":\"ACCEPTED\""));
        assertTrue(refreshedCustomerBookings.body().contains("\"providerName\":\"First Plumber\""));

        HttpResponse<String> forbiddenResponse = send(
                "PUT",
                "/api/service-requests/" + savedBooking.getId() + "/accept",
                token,
                null
        );
        assertEquals(403, forbiddenResponse.statusCode());
    }

    private String registerAndLoginProvider(String name) throws Exception {
        String email = "provider-" + UUID.randomUUID() + "@example.test";
        String password = UUID.randomUUID().toString();
        HttpResponse<String> registration = send(
                "POST",
                "/api/auth/register",
                null,
                "{\"fullName\":\"" + name + "\",\"email\":\"" + email
                        + "\",\"password\":\"" + password
                        + "\",\"role\":\"PROVIDER\",\"phone\":\"9876543210\","
                        + "\"serviceCategory\":\"PLUMBER\","
                        + "\"experience\":\"1-3_YEARS\","
                        + "\"serviceArea\":\"North District\"}"
        );
        assertEquals(201, registration.statusCode());
        HttpResponse<String> login = send(
                "POST",
                "/api/auth/login",
                null,
                "{\"email\":\"" + email + "\",\"password\":\""
                        + password + "\",\"role\":\"PROVIDER\"}"
        );
        assertEquals(200, login.statusCode());
        Matcher tokenMatcher = Pattern.compile("\"accessToken\":\"([^\"]+)\"")
                .matcher(login.body());
        assertTrue(tokenMatcher.find());
        return tokenMatcher.group(1);
    }

    private HttpResponse<String> send(
            String method,
            String path,
            String token,
            String body
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + path))
                .header("Accept", "application/json");
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        return HttpClient.newHttpClient().send(
                builder.method(method, publisher).build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

}
