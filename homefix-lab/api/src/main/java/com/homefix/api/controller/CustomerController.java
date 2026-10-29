package com.homefix.api.controller;

import com.homefix.api.dto.AddressRequest;
import com.homefix.api.model.Address;
import com.homefix.api.model.CustomerProfile;
import com.homefix.api.model.User;
import com.homefix.api.repository.AddressRepository;
import com.homefix.api.repository.CustomerProfileRepository;
import com.homefix.api.repository.UserRepository;
import com.homefix.api.service.DistanceCalculator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    private final UserRepository userRepository;
    private final CustomerProfileRepository profileRepository;
    private final AddressRepository addressRepository;

    public CustomerController(
            UserRepository userRepository,
            CustomerProfileRepository profileRepository,
            AddressRepository addressRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.addressRepository = addressRepository;
    }

    @GetMapping("/profile")
    public ResponseEntity<?> profile(@AuthenticationPrincipal Jwt jwt) {
        User user = currentCustomer(jwt);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Customer account required."));
        }
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "fullName", user.getFullName(),
                "email", user.getEmail(),
                "phone", user.getPhone() == null ? "" : user.getPhone()
        ));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> values) {
        User user = currentCustomer(jwt);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Customer account required."));
        }
        String fullName = values.get("fullName");
        String phone = values.get("phone");
        if (fullName == null || fullName.isBlank() || fullName.trim().length() > 120
                || phone == null || !phone.trim().matches("[0-9]{10}")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Enter a name and a valid 10-digit phone number."));
        }
        user.setFullName(fullName.trim());
        user.setPhone(phone.trim());
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Profile updated."));
    }

    @GetMapping("/addresses")
    public ResponseEntity<?> addresses(@AuthenticationPrincipal Jwt jwt) {
        User user = currentCustomer(jwt);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Customer account required."));
        }
        return ResponseEntity.ok(addressRepository
                .findByUser_IdOrderByPrimaryAddressDescCreatedAtDesc(user.getId())
                .stream()
                .map(this::addressView)
                .toList());
    }

    @PostMapping("/addresses")
    @Transactional
    public ResponseEntity<?> addAddress(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddressRequest request) {
        User user = currentCustomer(jwt);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Customer account required."));
        }
        if (addressRepository.countByUser_Id(user.getId()) >= 20) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "A maximum of 20 saved addresses is allowed."));
        }
        if ((request.latitude() == null) != (request.longitude() == null)
                || (request.latitude() != null
                && !DistanceCalculator.validCoordinates(request.latitude(), request.longitude()))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Provide a valid latitude and longitude pair."));
        }
        Address address = new Address();
        address.setUser(user);
        address.setLabel(request.label().trim());
        address.setStreetAddress(request.streetAddress().trim());
        address.setCity(request.city().trim());
        address.setPostalCode(request.postalCode());
        address.setLatitude(request.latitude());
        address.setLongitude(request.longitude());
        address.setPrimaryAddress(request.primaryAddress());
        if (request.primaryAddress()) {
            addressRepository.findByUser_IdOrderByPrimaryAddressDescCreatedAtDesc(user.getId())
                    .forEach(existing -> existing.setPrimaryAddress(false));
        }
        Address saved = addressRepository.save(address);
        CustomerProfile profile = profileRepository.findByUser_Id(user.getId())
                .orElseGet(() -> new CustomerProfile(user));
        profile.setPreferredAddress(saved.getStreetAddress() + ", " + saved.getCity());
        profileRepository.save(profile);
        return ResponseEntity.status(HttpStatus.CREATED).body(addressView(saved));
    }

    private Map<String, Object> addressView(Address address) {
        return Map.of(
                "id", address.getId(),
                "label", address.getLabel(),
                "streetAddress", address.getStreetAddress(),
                "city", address.getCity(),
                "postalCode", address.getPostalCode() == null ? "" : address.getPostalCode(),
                "primaryAddress", address.isPrimaryAddress()
        );
    }

    private User currentCustomer(Jwt jwt) {
        try {
            Long userId = Long.valueOf(jwt.getSubject());
            return userRepository.findById(userId)
                    .filter(user -> user.isActive()
                            && "CUSTOMER".equalsIgnoreCase(user.getRole()))
                    .orElse(null);
        } catch (NumberFormatException | NullPointerException exception) {
            return null;
        }
    }
}
