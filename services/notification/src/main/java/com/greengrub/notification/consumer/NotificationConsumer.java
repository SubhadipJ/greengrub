package com.greengrub.notification.consumer;

import com.greengrub.notification.dto.Donation;
import com.greengrub.notification.enums.DonationStatus;
import com.greengrub.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "donation-topic", groupId = "notificationGroup")
    public void handleDonationEvent(Donation event) {
        log.info("Received donation event -> ID: {}, Donor: {}, Status: {}, Items: {}",
                event.donationId(), event.donorName(), event.status(), event.items().size());

        DonationStatus eventType;
        try {
            eventType = event.status() != null
                    ? DonationStatus.valueOf(event.status())
                    : DonationStatus.ACTIVE;
        } catch (IllegalArgumentException e) {
            log.warn("Unknown donation status '{}' for donationId: {} — defaulting to ACTIVE",
                    event.status(), event.donationId());
            eventType = DonationStatus.ACTIVE;
        }

        notificationService.processNotification(event, eventType);
    }
}
