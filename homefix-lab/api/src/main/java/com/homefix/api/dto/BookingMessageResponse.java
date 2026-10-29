package com.homefix.api.dto;

import java.time.Instant;

public record BookingMessageResponse(
        Long id,
        Long senderId,
        String senderName,
        String senderRole,
        String message,
        Instant sentAt
) {
}
