package com.homefix.api.controller;

import com.homefix.api.model.Notification;
import com.homefix.api.repository.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal Jwt jwt) {
        Long userId = userId(jwt);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required."));
        }
        return ResponseEntity.ok(notificationRepository
                .findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::view)
                .toList());
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        Long userId = userId(jwt);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required."));
        }
        Notification notification = notificationRepository.findByIdAndUser_Id(id, userId)
                .orElse(null);
        if (notification == null) {
            return ResponseEntity.notFound().build();
        }
        notification.markRead();
        notificationRepository.save(notification);
        return ResponseEntity.ok(view(notification));
    }

    private Map<String, Object> view(Notification notification) {
        return Map.of(
                "id", notification.getId(),
                "title", notification.getTitle(),
                "message", notification.getMessage(),
                "type", notification.getType() == null ? "GENERAL" : notification.getType(),
                "read", notification.getReadAt() != null,
                "createdAt", notification.getCreatedAt()
        );
    }

    private Long userId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException | NullPointerException exception) {
            return null;
        }
    }
}
