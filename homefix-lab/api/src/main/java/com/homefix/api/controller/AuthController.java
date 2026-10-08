package com.homefix.api.controller;

import com.homefix.api.dto.AuthRequest;
import com.homefix.api.model.User;
import com.homefix.api.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody AuthRequest request) {

        Map<String, Object> response = new HashMap<>();

        if (request.getEmail() == null ||
            request.getEmail().isBlank() ||
            request.getPassword() == null ||
            request.getPassword().isBlank()) {

            response.put("message", "Email and password are required");
            return ResponseEntity.badRequest().body(response);
        }

        Optional<User> existing =
                userRepository.findByEmail(request.getEmail().trim());

        if (existing.isPresent()) {
            response.put("message", "User already exists");
            return ResponseEntity.badRequest().body(response);
        }

        User user = new User();

        user.setFullName(
                request.getFullName() == null
                        ? ""
                        : request.getFullName().trim()
        );

        user.setEmail(request.getEmail().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        userRepository.save(user);

        response.put("message", "Registration successful");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody AuthRequest request) {

        Map<String, Object> response = new HashMap<>();

        if (request.getEmail() == null ||
            request.getPassword() == null) {

            response.put("message", "Email and password are required");
            return ResponseEntity.badRequest().body(response);
        }

        Optional<User> optionalUser =
                userRepository.findByEmail(request.getEmail().trim());

        if (optionalUser.isEmpty()) {
            response.put("message", "Invalid email or password");
            return ResponseEntity.badRequest().body(response);
        }

        User user = optionalUser.get();
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPassword())
                || user.getPassword().equals(request.getPassword());

        if (!passwordMatches) {
            response.put("message", "Invalid email or password");
            return ResponseEntity.badRequest().body(response);
        }

        if (!user.getPassword().startsWith("$2a$") && !user.getPassword().startsWith("$2b$") && !user.getPassword().startsWith("$2y$")) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            userRepository.save(user);
        }

        Map<String, Object> userData = new HashMap<>();

        userData.put("id", user.getId());
        userData.put("email", user.getEmail());
        userData.put("fullName", user.getFullName());

        response.put("message", "Login successful");
        response.put("user", userData);

        return ResponseEntity.ok(response);
    }
}