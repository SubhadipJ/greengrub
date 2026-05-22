package com.greengrub.notification.service;

import com.greengrub.notification.dto.Donation;
import com.greengrub.notification.email.EmailService;
import com.greengrub.notification.entity.NotificationDocument;
import com.greengrub.notification.enums.DonationStatus;
import com.greengrub.notification.enums.NotificationStatus;
import com.greengrub.notification.mapper.NotificationMapper;
import com.greengrub.notification.repository.NotificationMongoRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@Profile("local")
@RequiredArgsConstructor
public class MongoNotificationService implements NotificationService {

    private final NotificationMongoRepository repository;
    private final NotificationMapper mapper;
    private final EmailService emailService;

    @Override
    @Retry(name = "notificationRetry")
    @CircuitBreaker(name = "notificationBreaker")
    public void processNotification(Donation donation, DonationStatus eventType) {
        NotificationDocument document = mapper.toDocument(donation, eventType);
        document = repository.save(document);

        try {
            emailService.sendDonationThankYouEmail(donation, resolveSubject(eventType));
            document.setStatus(NotificationStatus.SENT);
            document.setSentAt(LocalDateTime.now());
            log.info("Notification sent successfully for donationId: {}", donation.donationId());
        } catch (Exception e) {
            document.setStatus(NotificationStatus.FAILED);
            document.setFailureReason(e.getMessage());
            log.error("Email send failed for donationId: {}", donation.donationId(), e);
        }

        document.setUpdatedAt(LocalDateTime.now());
        repository.save(document);
    }

    @Override
    public List<NotificationDocument> getNotificationsByDonationId(String donationId) {
        return repository.findByDonationId(donationId);
    }

    @Override
    public List<NotificationDocument> getNotificationsByRecipient(String recipient) {
        return repository.findByRecipient(recipient);
    }

    private String resolveSubject(DonationStatus eventType) {
        return switch (eventType) {
            case ACTIVE -> "Thank you for changing lives with your donation!";
            case CLAIMED -> "Your donation has been claimed!";
            case CANCELLED -> "Your donation has been cancelled";
        };
    }
}
