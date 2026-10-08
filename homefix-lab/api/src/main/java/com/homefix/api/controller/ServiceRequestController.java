package com.homefix.api.controller;

import com.homefix.api.dto.ServiceRequest;
import com.homefix.api.model.BookingEntity;
import com.homefix.api.model.User;
import com.homefix.api.repository.BookingRepository;
import com.homefix.api.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ServiceRequestController {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    public ServiceRequestController(
            BookingRepository bookingRepository,
            UserRepository userRepository) {

        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
    }

    // CREATE
    @PostMapping("/service-requests")
    public BookingEntity createServiceRequest(
            @RequestParam Long userId,
            @RequestBody ServiceRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String newId = "HF" +
                (1000 + (int) (Math.random() * 9000));

        BookingEntity booking = new BookingEntity(
                newId,
                request.getCustomerName(),
                request.getService(),
                "Provider will be assigned",
                "PENDING",
                user
        );

        return bookingRepository.save(booking);
    }

    // READ - only logged-in user's bookings
    @GetMapping("/service-requests")
public List<BookingEntity> getAllRequests(
        @RequestParam Long userId) {

    User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

    return bookingRepository.findByUserOrderByIdDesc(user);
}

    // UPDATE
    @PutMapping("/service-requests/{id}")
    public BookingEntity updateServiceRequest(
            @PathVariable Long id,
            @RequestBody ServiceRequest request) {

        BookingEntity booking = bookingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        booking.setCustomerName(request.getCustomerName());
        booking.setService(request.getService());

        return bookingRepository.save(booking);
    }

    // DELETE
    @DeleteMapping("/service-requests/{id}")
    public String deleteServiceRequest(@PathVariable Long id) {

        if (!bookingRepository.existsById(id)) {
            return "Service request not found";
        }

        bookingRepository.deleteById(id);

        return "Service request deleted successfully";
    }
}