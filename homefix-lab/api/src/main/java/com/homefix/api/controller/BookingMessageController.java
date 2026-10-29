package com.homefix.api.controller;

import com.homefix.api.dto.BookingMessageRequest;
import com.homefix.api.dto.BookingMessageResponse;
import com.homefix.api.model.BookingEntity;
import com.homefix.api.model.BookingMessage;
import com.homefix.api.model.User;
import com.homefix.api.repository.BookingMessageRepository;
import com.homefix.api.repository.BookingRepository;
import com.homefix.api.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/service-requests/{bookingId}/messages")
public class BookingMessageController {

    private static final java.util.Set<String> CHAT_OPEN_STATUSES = java.util.Set.of(
            "ACCEPTED", "CONFIRMED", "ON_THE_WAY", "ARRIVED", "IN_PROGRESS");

    private final BookingRepository bookingRepository;
    private final BookingMessageRepository messageRepository;
    private final UserRepository userRepository;

    public BookingMessageController(
            BookingRepository bookingRepository,
            BookingMessageRepository messageRepository,
            UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMessages(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal Jwt jwt) {
        Conversation conversation = findConversation(bookingId, jwt);
        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }
        List<BookingMessageResponse> messages = messageRepository
                .findByBooking_IdOrderBySentAtAscIdAsc(bookingId)
                .stream()
                .map(BookingMessageController::toView)
                .toList();
        return ResponseEntity.ok(messages);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> sendMessage(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BookingMessageRequest request) {
        Conversation conversation = findConversation(bookingId, jwt);
        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }

        BookingMessage message = new BookingMessage();
        message.setBooking(conversation.booking());
        message.setSender(conversation.viewer());
        message.setMessage(request.message().trim());
        BookingMessage saved = messageRepository.save(message);
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(saved));
    }

    private Conversation findConversation(Long bookingId, Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            return null;
        }
        Long viewerId;
        try {
            viewerId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            return null;
        }
        User viewer = userRepository.findById(viewerId).orElse(null);
        BookingEntity booking = bookingRepository.findById(bookingId).orElse(null);
        if (viewer == null || !viewer.isActive() || booking == null
                || booking.getProvider() == null
                || booking.getStatus() == null
                || !CHAT_OPEN_STATUSES.contains(normalizeStatus(booking.getStatus()))) {
            return null;
        }

        boolean customerOwnsBooking = "CUSTOMER".equalsIgnoreCase(viewer.getRole())
                && booking.getUser() != null
                && booking.getUser().getId().equals(viewer.getId());
        boolean assignedProvider = "PROVIDER".equalsIgnoreCase(viewer.getRole())
                && booking.getProvider().getId().equals(viewer.getId())
                && viewer.isProviderApproved();
        if (!customerOwnsBooking && !assignedProvider) {
            return null;
        }
        return new Conversation(booking, viewer);
    }

    private static String normalizeStatus(String status) {
        return status.trim().toUpperCase(java.util.Locale.ROOT)
                .replaceAll("[\\s-]+", "_");
    }

    private static BookingMessageResponse toView(BookingMessage message) {
        return new BookingMessageResponse(
                message.getId(),
                message.getSender().getId(),
                message.getSender().getFullName(),
                message.getSender().getRole(),
                message.getMessage(),
                message.getSentAt());
    }

    private record Conversation(BookingEntity booking, User viewer) {
    }

}
