package com.greengrub.notification.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greengrub.notification.dto.Donation;
import com.greengrub.notification.entity.NotificationDocument;
import com.greengrub.notification.entity.NotificationEntity;
import com.greengrub.notification.enums.DonationStatus;
import com.greengrub.notification.enums.NotificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class NotificationMapper {

    private final ObjectMapper objectMapper;

    public NotificationDocument toDocument(Donation donation, DonationStatus eventType) {
        return NotificationDocument.builder()
                .donationId(donation.donationId())
                .eventType(eventType)
                .status(NotificationStatus.PENDING)
                .recipient(donation.donorEmail())
                .payload(serializeToJson(donation))
                .notificationTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public NotificationEntity toEntity(Donation donation, DonationStatus eventType) {
        return NotificationEntity.builder()
                .donationId(donation.donationId())
                .eventType(eventType)
                .status(NotificationStatus.PENDING)
                .recipient(donation.donorEmail())
                .payload(serializeToJson(donation))
                .notificationTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private String serializeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize payload to JSON", e);
        }
    }
}
