package com.homefix.api.controller;

import com.homefix.api.dto.AuthRequest;
import com.homefix.api.model.User;
import com.homefix.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void registerEndpointShouldCreateUser() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByEmail("test.user@example.com")).thenReturn(Optional.empty());

        AuthController controller = new AuthController(repository);
        AuthRequest request = new AuthRequest();
        request.setFullName("Test User");
        request.setEmail("test.user@example.com");
        request.setPassword("password123");

        ResponseEntity<Map<String, Object>> response = controller.register(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Registration successful", response.getBody().get("message"));

        var userCaptor = forClass(User.class);
        verify(repository).save(userCaptor.capture());
        assertNotEquals("password123", userCaptor.getValue().getPassword());
    }

    @Test
    void loginEndpointShouldAcceptRegisteredUser() {
        UserRepository repository = mock(UserRepository.class);
        User savedUser = new User("Login User", "login.user@example.com", "secret123");
        when(repository.findByEmail("login.user@example.com")).thenReturn(Optional.of(savedUser));

        AuthController controller = new AuthController(repository);
        AuthRequest request = new AuthRequest();
        request.setEmail("login.user@example.com");
        request.setPassword("secret123");

        ResponseEntity<Map<String, Object>> response = controller.login(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Login successful", response.getBody().get("message"));
        assertNotNull(response.getBody().get("user"));
    }
}
