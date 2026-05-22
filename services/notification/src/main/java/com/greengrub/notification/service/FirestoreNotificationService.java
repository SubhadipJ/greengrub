package com.greengrub.notification.service;

import com.greengrub.notification.dto.Donation;
import com.greengrub.notification.email.EmailService;
import com.greengrub.notification.entity.NotificationEntity;
import com.greengrub.notification.enums.DonationStatus;
import com.greengrub.notification.enums.NotificationStatus;
import com.greengrub.notification.mapper.NotificationMapper;
import com.greengrub.notification.repository.NotificationFirestoreRepository;
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
@Profile("k8s")
@RequiredArgsConstructor
public class FirestoreNotificationService implements NotificationService {

    private final NotificationFirestoreRepository repository;
    private final NotificationMapper mapper;
    private final EmailService emailService;

    @Override
    @Retry(name = "notificationRetry")
    @CircuitBreaker(name = "notificationBreaker")
    public void processNotification(Donation donation, DonationStatus eventType) {
        NotificationEntity entity = mapper.toEntity(donation, eventType);
        entity = repository.save(entity).block();

        try {
            emailService.sendDonationThankYouEmail(donation, resolveSubject(eventType));
            entity.setStatus(NotificationStatus.SENT);
            entity.setSentAt(LocalDateTime.now());
            log.info("Notification sent successfully for donationId: {}", donation.donationId());
        } catch (Exception e) {
            entity.setStatus(NotificationStatus.FAILED);
            entity.setFailureReason(e.getMessage());
            log.error("Email send failed for donationId: {}", donation.donationId(), e);
        }

        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity).block();
    }

    @Override
    public List<NotificationEntity> getNotificationsByDonationId(String donationId) {
        return repository.findByDonationId(donationId).collectList().block();
    }

    @Override
    public List<NotificationEntity> getNotificationsByRecipient(String recipient) {
        return repository.findByRecipient(recipient).collectList().block();
    }

    private String resolveSubject(DonationStatus eventType) {
        return switch (eventType) {
            case ACTIVE -> "Thank you for changing lives with your donation!";
            case CLAIMED -> "Your donation has been claimed!";
            case CANCELLED -> "Your donation has been cancelled";
        };
    }
}
