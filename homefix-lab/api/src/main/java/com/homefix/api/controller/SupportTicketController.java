package com.homefix.api.controller;

import com.homefix.api.dto.SupportTicketRequest;
import com.homefix.api.model.BookingEntity;
import com.homefix.api.model.SupportTicket;
import com.homefix.api.model.User;
import com.homefix.api.repository.BookingRepository;
import com.homefix.api.repository.SupportTicketRepository;
import com.homefix.api.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/support/tickets")
public class SupportTicketController {

    private final SupportTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    public SupportTicketController(
            SupportTicketRepository ticketRepository,
            UserRepository userRepository,
            BookingRepository bookingRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal Jwt jwt) {
        User user = currentUser(jwt);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required."));
        }
        return ResponseEntity.ok(ticketRepository
                .findByCustomer_IdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::view)
                .toList());
    }

    @PostMapping
    public ResponseEntity<?> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SupportTicketRequest request) {
        User user = currentUser(jwt);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required."));
        }
        BookingEntity booking = null;
        if (request.bookingId() != null) {
            booking = bookingRepository.findById(request.bookingId()).orElse(null);
            if (booking == null || !booking.getUser().getId().equals(user.getId())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "The booking could not be found for this account."));
            }
        }
        SupportTicket ticket = new SupportTicket();
        ticket.setCustomer(user);
        ticket.setBooking(booking);
        ticket.setSubject(request.subject().trim());
        ticket.setDescription(request.description().trim());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(view(ticketRepository.save(ticket)));
    }

    private Map<String, Object> view(SupportTicket ticket) {
        return Map.of(
                "id", ticket.getId(),
                "subject", ticket.getSubject(),
                "description", ticket.getDescription(),
                "status", ticket.getStatus(),
                "resolution", ticket.getResolution() == null ? "" : ticket.getResolution(),
                "bookingId", ticket.getBooking() == null ? "" : ticket.getBooking().getBookingId(),
                "createdAt", ticket.getCreatedAt()
        );
    }

    private User currentUser(Jwt jwt) {
        try {
            return userRepository.findById(Long.valueOf(jwt.getSubject()))
                    .filter(User::isActive)
                    .orElse(null);
        } catch (NumberFormatException | NullPointerException exception) {
            return null;
        }
    }
}
