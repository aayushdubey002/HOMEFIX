package com.homefix.api.controller;

import com.homefix.api.dto.AuthRequest;
import com.homefix.api.model.CustomerProfile;
import com.homefix.api.model.ProviderProfile;
import com.homefix.api.model.ProviderSkill;
import com.homefix.api.model.ServiceCategory;
import com.homefix.api.model.User;
import com.homefix.api.repository.CustomerProfileRepository;
import com.homefix.api.repository.ProviderProfileRepository;
import com.homefix.api.repository.ProviderSkillRepository;
import com.homefix.api.repository.ServiceCategoryRepository;
import com.homefix.api.repository.UserRepository;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
                    Pattern.CASE_INSENSITIVE);

    private static final Set<String> PROVIDER_EXPERIENCE = Set.of(
            "LESS_THAN_1_YEAR",
            "1_3_YEARS",
            "3_5_YEARS",
            "5_PLUS_YEARS"
    );

    private final UserRepository userRepository;
    private final JwtEncoder jwtEncoder;
    private final PasswordEncoder passwordEncoder;
    private final CustomerProfileRepository customerProfileRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final ProviderSkillRepository providerSkillRepository;
    private final ServiceCategoryRepository categoryRepository;

    public AuthController(
            UserRepository userRepository,
            JwtEncoder jwtEncoder,
            PasswordEncoder passwordEncoder,
            CustomerProfileRepository customerProfileRepository,
            ProviderProfileRepository providerProfileRepository,
            ProviderSkillRepository providerSkillRepository,
            ServiceCategoryRepository categoryRepository
    ) {
        this.userRepository = userRepository;
        this.jwtEncoder = jwtEncoder;
        this.passwordEncoder = passwordEncoder;
        this.customerProfileRepository = customerProfileRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.providerSkillRepository = providerSkillRepository;
        this.categoryRepository = categoryRepository;
    }

    // REGISTER
    @PostMapping("/register")
    @Transactional
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody AuthRequest request) {

        Map<String, Object> response = new HashMap<>();

        // Validate full name
        if (request.getFullName() == null
                || request.getFullName().isBlank()) {
            response.put("message", "Full name is required.");
            return ResponseEntity.badRequest().body(response);
        }

        // Validate email
        if (request.getEmail() == null
                || request.getEmail().isBlank()) {
            response.put("message", "Email is required.");
            return ResponseEntity.badRequest().body(response);
        }

        String email = request.getEmail().trim();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            response.put("message", "Enter a valid email address.");
            return ResponseEntity.badRequest().body(response);
        }

        // Validate password
        if (request.getPassword() == null
                || request.getPassword().length() < 8) {
            response.put(
                    "message",
                    "Password must be at least 8 characters."
            );
            return ResponseEntity.badRequest().body(response);
        }

        email = email
                .toLowerCase(Locale.ROOT);

        // Normalize role
        String role = request.getRole();

        if (role == null || role.isBlank()) {
            role = "CUSTOMER";
        } else {
            role = role.trim().toUpperCase(Locale.ROOT);
        }

        if (!role.equals("CUSTOMER")
                && !role.equals("PROVIDER")) {
            response.put(
                    "message",
                    "Role must be CUSTOMER or PROVIDER."
            );
            return ResponseEntity.badRequest().body(response);
        }

        if (request.getPhone() != null
                && !request.getPhone().isBlank()
                && !request.getPhone().trim().matches("[0-9]{10}")) {
            response.put("message", "Enter a valid 10-digit phone number.");
            return ResponseEntity.badRequest().body(response);
        }

        // Provider-specific validation
        if (role.equals("PROVIDER")) {

            if (request.getPhone() == null
                    || !request.getPhone().trim().matches("[0-9]{10}")) {
                response.put("message", "Enter a valid 10-digit phone number.");
                return ResponseEntity.badRequest().body(response);
            }

            if (request.getServiceCategory() == null
                    || request.getServiceCategory().isBlank()) {
                response.put(
                        "message",
                        "Service category is required."
                );
                return ResponseEntity.badRequest().body(response);
            }

            if (request.getExperience() == null
                    || request.getExperience().isBlank()) {
                response.put(
                        "message",
                        "Experience is required."
                );
                return ResponseEntity.badRequest().body(response);
            }

            String experience = normalizeProviderValue(
                    request.getExperience()
            );
            if (!PROVIDER_EXPERIENCE.contains(experience)) {
                response.put("message", "Select a valid experience range.");
                return ResponseEntity.badRequest().body(response);
            }

            if (request.getServiceArea() == null
                    || request.getServiceArea().isBlank()) {
                response.put(
                        "message",
                        "Service area is required."
                );
                return ResponseEntity.badRequest().body(response);
            }
        }

        // Check duplicate email
        Optional<User> existingUser =
                userRepository.findByEmail(email);

        if (existingUser.isPresent()) {
            response.put("message", "User already exists.");
            return ResponseEntity.badRequest().body(response);
        }

        Optional<ServiceCategory> providerCategory = Optional.empty();
        if (role.equals("PROVIDER")) {
            String requestedCategory = request.getServiceCategory().trim();
            String normalizedCategoryCode = categoryCode(requestedCategory);
            providerCategory = categoryRepository.findByServiceCodeIgnoreCase(requestedCategory)
                    .or(() -> categoryRepository.findByServiceCodeIgnoreCase(normalizedCategoryCode))
                    .or(() -> categoryRepository.findBySlugIgnoreCase(requestedCategory));
            if (providerCategory.isEmpty() || !providerCategory.get().isActive()) {
                response.put("message", "The selected service category is unavailable.");
                return ResponseEntity.badRequest().body(response);
            }
        }

        // Create user
        User user = new User();

        user.setFullName(request.getFullName().trim());
        user.setEmail(email);
        user.setPassword(
                passwordEncoder.encode(request.getPassword())
        );
        user.setRole(role);

        // Save provider details when registering as provider
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            user.setPhone(request.getPhone().trim());
        }

        if (role.equals("PROVIDER")) {
            user.setServiceCategory(
                    request.getServiceCategory().trim()
            );
            user.setExperience(request.getExperience().trim());
            user.setServiceArea(request.getServiceArea().trim());
        }

        User savedUser = userRepository.save(user);
        if (role.equals("CUSTOMER")) {
            customerProfileRepository.save(new CustomerProfile(savedUser));
        } else {
            ServiceCategory selectedCategory = providerCategory.orElseThrow();
            ProviderProfile profile = providerProfileRepository.save(
                    new ProviderProfile(savedUser));
            providerSkillRepository.save(new ProviderSkill(
                    profile,
                    selectedCategory,
                    selectedCategory.getStartingPrice()
            ));
        }

        response.put("message", role.equals("PROVIDER")
                ? "Registration submitted. Your provider profile is awaiting admin verification."
                : "Registration successful.");
        response.put("role", role);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    // LOGIN
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody AuthRequest request) {

        Map<String, Object> response = new HashMap<>();

        if (request.getEmail() == null
                || request.getEmail().isBlank()
                || request.getPassword() == null
                || request.getPassword().isBlank()) {

            response.put(
                    "message",
                    "Email and password are required."
            );

            return ResponseEntity.badRequest().body(response);
        }

        String email = request.getEmail()
                .trim();

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            response.put("message", "Enter a valid email address.");
            return ResponseEntity.badRequest().body(response);
        }

        email = email
                .toLowerCase(Locale.ROOT);

        Optional<User> optionalUser =
                userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            response.put(
                    "message",
                    "Invalid email or password."
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(response);
        }

        User user = optionalUser.get();
        if (!user.isActive()) {
            response.put("message", "This account is inactive. Contact HomeFix support.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        String storedPassword = user.getPassword();

        if (storedPassword == null
                || !passwordEncoder.matches(
                        request.getPassword(),
                        storedPassword
                )) {

            response.put(
                    "message",
                    "Invalid email or password."
            );

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(response);
        }

        String role = user.getRole();

        if (role == null || role.isBlank()) {
            role = "CUSTOMER";
        } else {
            role = role.toUpperCase(Locale.ROOT);
        }

        // If the login request specifies a role, verify it
        if (request.getRole() != null
                && !request.getRole().isBlank()) {

            String requestedRole = request.getRole()
                    .trim()
                    .toUpperCase(Locale.ROOT);

            if (!requestedRole.equals(role)) {
                response.put(
                        "message",
                        "This account is registered as " + role + "."
                );

                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(response);
            }
        }

        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("homefix")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(1, ChronoUnit.HOURS))
                .subject(user.getId().toString())
                .claim("role", role)
                .build();
        String accessToken = jwtEncoder.encode(
                JwtEncoderParameters.from(
                        org.springframework.security.oauth2.jwt.JwsHeader
                                .with(MacAlgorithm.HS256)
                                .build(),
                        claims
                )
        ).getTokenValue();

        response.put("accessToken", accessToken);
        response.put("tokenType", "Bearer");
        response.put("expiresIn", 3600);
        response.put("user", toUserData(user, role));
        response.put("message", "Login successful.");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<?> currentUser(@AuthenticationPrincipal Jwt jwt) {
        Long userId = parseUserId(jwt);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid authentication token."));
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authenticated account no longer exists."));
        }

        String role = normalizedRole(user.getRole());
        if (!"CUSTOMER".equals(role) && !"PROVIDER".equals(role)
                && !"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Account role is not supported."));
        }
        return ResponseEntity.ok(Map.of("user", toUserData(user, role)));
    }

    private Map<String, Object> toUserData(User user, String role) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("email", user.getEmail());
        userData.put("fullName", user.getFullName());
        userData.put("role", role);
        if (user.getPhone() != null) {
            userData.put("phone", user.getPhone());
        }
        if ("PROVIDER".equals(role)) {
            userData.put("serviceCategory", user.getServiceCategory());
            userData.put("experience", user.getExperience());
            userData.put("serviceArea", user.getServiceArea());
            userData.put("providerApproved", user.isProviderApproved());
        }
        return userData;
    }

    private Long parseUserId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException | NullPointerException exception) {
            return null;
        }
    }

    private String normalizedRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeProviderValue(String value) {
        return value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }

    private String categoryCode(String category) {
        return switch (normalizeProviderValue(category)) {
            case "ELECTRICIAN", "ELECTRICAL" -> "ELECTRICAL";
            case "PLUMBER", "PLUMBING" -> "PLUMBING";
            case "AC REPAIR", "AC_REPAIR", "AIR CONDITIONING", "AC SERVICE",
                    "AC REPAIR SERVICE" -> "AC REPAIR";
            case "HOME CLEANING", "CLEANING" -> "CLEANING";
            case "CARPENTER", "CARPENTRY" -> "CARPENTER";
            case "APPLIANCE REPAIR", "APPLIANCE_REPAIR",
                    "APPLIANCE REPAIR SERVICE", "APPLIANCE_REPAIR_SERVICE" ->
                    "APPLIANCE REPAIR";
            default -> normalizeProviderValue(category);
        };
    }
}