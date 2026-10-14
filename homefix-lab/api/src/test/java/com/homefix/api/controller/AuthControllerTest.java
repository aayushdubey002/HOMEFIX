package com.homefix.api.controller;

import com.homefix.api.dto.AuthRequest;
import com.homefix.api.model.User;
import com.homefix.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void registerEndpointShouldCreateUser() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByEmail("test.user@example.com")).thenReturn(Optional.empty());

        AuthController controller = controller(repository);
        AuthRequest request = new AuthRequest();
        request.setFullName("Test User");
        request.setEmail("test.user@example.com");
        request.setPassword("password123");
        request.setPhone("9876543210");

        ResponseEntity<Map<String, Object>> response = controller.register(request);

        assertEquals(201, response.getStatusCode().value());
        assertEquals("Registration successful.", response.getBody().get("message"));

        var userCaptor = forClass(User.class);
        verify(repository).save(userCaptor.capture());
        assertNotEquals("password123", userCaptor.getValue().getPassword());
        assertEquals("9876543210", userCaptor.getValue().getPhone());
    }

    @Test
    void loginEndpointShouldAcceptRegisteredUser() {
        UserRepository repository = mock(UserRepository.class);
        assertSuccessfulLogin(repository, "CUSTOMER");
        assertSuccessfulLogin(mock(UserRepository.class), "PROVIDER");
    }

    @Test
    void providerRegistrationStoresValidatedDetailsAndEncodedPassword() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByEmail("provider@example.com"))
                .thenReturn(Optional.empty());
        AuthRequest request = validProviderRequest();
        request.setEmail(" Provider@Example.com ");
        String password = UUID.randomUUID().toString();
        request.setPassword(password);

        ResponseEntity<Map<String, Object>> response =
                controller(repository).register(request);

        assertEquals(201, response.getStatusCode().value());
        assertEquals("PROVIDER", response.getBody().get("role"));

        var userCaptor = forClass(User.class);
        verify(repository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("provider@example.com", savedUser.getEmail());
        assertEquals("PROVIDER", savedUser.getRole());
        assertEquals("9876543210", savedUser.getPhone());
        assertEquals("AC_REPAIR", savedUser.getServiceCategory());
        assertEquals("3-5_YEARS", savedUser.getExperience());
        assertEquals("Central District", savedUser.getServiceArea());
        assertNotEquals(password, savedUser.getPassword());
        assertTrue(new BCryptPasswordEncoder().matches(
                password,
                savedUser.getPassword()
        ));
    }

    @Test
    void registrationRejectsMissingFieldsAndMalformedEmail() {
        assertInvalidCustomerRequest(request -> request.setFullName(" "));
        assertInvalidCustomerRequest(request -> request.setEmail(" "));
        assertInvalidCustomerRequest(request -> request.setEmail("not-an-email"));
        assertInvalidCustomerRequest(request -> request.setPassword("short"));
        assertInvalidCustomerRequest(request -> request.setPhone("123"));
    }

    @Test
    void loginRejectsMissingFieldsMalformedEmailAndWrongPassword() {
        UserRepository repository = mock(UserRepository.class);
        AuthController controller = controller(repository);

        AuthRequest request = new AuthRequest();
        request.setEmail(" ");
        request.setPassword("not-a-real-account-password");
        assertEquals(400, controller.login(request).getStatusCode().value());

        request.setEmail("invalid-email");
        assertEquals(400, controller.login(request).getStatusCode().value());

        request.setEmail("person@example.com");
        request.setPassword(" ");
        assertEquals(400, controller.login(request).getStatusCode().value());

        String validPassword = UUID.randomUUID().toString();
        User savedUser = new User(
                "Customer",
                "person@example.com",
                new BCryptPasswordEncoder().encode(validPassword)
        );
        when(repository.findByEmail("person@example.com"))
                .thenReturn(Optional.of(savedUser));
        request.setPassword("incorrect-password-value");
        assertEquals(401, controller.login(request).getStatusCode().value());
    }

    @Test
    void providerRegistrationRequiresValidPhoneCategoryExperienceAndArea() {
        assertInvalidProviderRequest(request -> request.setPhone("123"));
        assertInvalidProviderRequest(request -> request.setServiceCategory("Gardening"));
        assertInvalidProviderRequest(request -> request.setExperience("A long time"));
        assertInvalidProviderRequest(request -> request.setServiceArea(" "));
    }

    @Test
    void registrationDoesNotSaveUsersWithUnsupportedRoleOrDuplicateEmail() {
        UserRepository repository = mock(UserRepository.class);
        AuthController controller = controller(repository);

        AuthRequest invalidRole = validCustomerRequest();
        invalidRole.setRole("ADMIN");
        assertEquals(400, controller.register(invalidRole).getStatusCode().value());

        AuthRequest duplicate = validCustomerRequest();
        when(repository.findByEmail(duplicate.getEmail()))
                .thenReturn(Optional.of(new User()));
        assertEquals(400, controller.register(duplicate).getStatusCode().value());
        verify(repository, never()).save(any(User.class));
    }

    private void assertSuccessfulLogin(
            UserRepository repository,
            String role
    ) {
        String email = role.toLowerCase() + "@example.com";
        String password = UUID.randomUUID().toString();
        User savedUser = new User(
                role + " User",
                email,
                new BCryptPasswordEncoder().encode(password)
        );
        savedUser.setId(1L);
        savedUser.setRole(role);

        if ("PROVIDER".equals(role)) {
            savedUser.setPhone("9876543210");
            savedUser.setServiceCategory("PLUMBING");
            savedUser.setExperience("1-3_YEARS");
            savedUser.setServiceArea("North District");
        }

        when(repository.findByEmail(email)).thenReturn(Optional.of(savedUser));
        AuthRequest request = new AuthRequest();
        request.setEmail(email);
        request.setPassword(password);
        request.setRole(role);

        ResponseEntity<Map<String, Object>> response =
                controller(repository).login(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("test-access-token", response.getBody().get("accessToken"));
        Map<?, ?> userData = (Map<?, ?>) response.getBody().get("user");
        assertNotNull(userData);
        assertEquals(role, userData.get("role"));
        assertFalse(userData.containsKey("password"));
        assertEquals(1L, userData.get("id"));

        if ("PROVIDER".equals(role)) {
            assertEquals("9876543210", userData.get("phone"));
            assertEquals("PLUMBING", userData.get("serviceCategory"));
            assertEquals("1-3_YEARS", userData.get("experience"));
            assertEquals("North District", userData.get("serviceArea"));
        }
    }

    private void assertInvalidCustomerRequest(Consumer<AuthRequest> mutation) {
        UserRepository repository = mock(UserRepository.class);
        AuthRequest request = validCustomerRequest();
        mutation.accept(request);

        ResponseEntity<Map<String, Object>> response =
                controller(repository).register(request);

        assertEquals(400, response.getStatusCode().value());
        verify(repository, never()).save(any(User.class));
    }

    private void assertInvalidProviderRequest(Consumer<AuthRequest> mutation) {
        UserRepository repository = mock(UserRepository.class);
        AuthRequest request = validProviderRequest();
        mutation.accept(request);

        ResponseEntity<Map<String, Object>> response =
                controller(repository).register(request);

        assertEquals(400, response.getStatusCode().value());
        verify(repository, never()).save(any(User.class));
    }

    private AuthRequest validCustomerRequest() {
        AuthRequest request = new AuthRequest();
        request.setFullName("Customer");
        request.setEmail("customer@example.com");
        request.setPassword(UUID.randomUUID().toString());
        request.setRole("CUSTOMER");
        request.setPhone("9876543210");
        return request;
    }

    private AuthRequest validProviderRequest() {
        AuthRequest request = validCustomerRequest();
        request.setEmail("provider@example.com");
        request.setRole("PROVIDER");
        request.setPhone("9876543210");
        request.setServiceCategory("AC_REPAIR");
        request.setExperience("3-5_YEARS");
        request.setServiceArea("Central District");
        return request;
    }

    private AuthController controller(UserRepository repository) {
        JwtEncoder encoder = mock(JwtEncoder.class);
        when(encoder.encode(any(JwtEncoderParameters.class))).thenReturn(
                Jwt.withTokenValue("test-access-token")
                        .header("alg", "HS256")
                        .subject("1")
                        .claim("role", "CUSTOMER")
                        .build()
        );
        return new AuthController(
                repository,
                encoder,
                new BCryptPasswordEncoder()
        );
    }
}
