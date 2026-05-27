package com.greengrub.notification.service;

import com.greengrub.notification.dto.Customer;
import com.greengrub.notification.dto.DonatedItem;
import com.greengrub.notification.dto.Donation;
import com.greengrub.notification.email.EmailService;
import com.greengrub.notification.entity.NotificationEntity;
import com.greengrub.notification.enums.DonationStatus;
import com.greengrub.notification.enums.NotificationStatus;
import com.greengrub.notification.mapper.NotificationMapper;
import com.greengrub.notification.repository.NotificationFirestoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FirestoreNotificationServiceTest {

    @Mock
    private NotificationFirestoreRepository repository;

    @Mock
    private NotificationMapper mapper;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private FirestoreNotificationService service;

    private Donation donation;
    private NotificationEntity pendingEntity;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer("cust-002", "Carol", "White", "carol@example.com", "555-0500");
        donation = new Donation(
                "don-002", "Carol White", "carol@example.com",
                new BigDecimal("200.00"),
                LocalDateTime.of(2024, 7, 4, 8, 0, 0),
                customer, "GreenGrub Org", "ACTIVE",
                List.of(new DonatedItem("Lentils", 6, "kg", "Legumes"))
        );

        pendingEntity = NotificationEntity.builder()
                .id("notif-002")
                .donationId("don-002")
                .status(NotificationStatus.PENDING)
                .recipient("carol@example.com")
                .build();

        when(mapper.toEntity(any(Donation.class), any(DonationStatus.class))).thenReturn(pendingEntity);
        when(repository.save(any(NotificationEntity.class))).thenReturn(Mono.just(pendingEntity));
    }

    // ── processNotification — success ─────────────────────────────────────────

    @Test
    void processNotification_active_sendsEmailAndSetsStatusSent() throws Exception {
        service.processNotification(donation, DonationStatus.ACTIVE);

        verify(emailService).sendDonationThankYouEmail(
                eq(donation),
                eq("Thank you for changing lives with your donation!"));
        assertThat(pendingEntity.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(pendingEntity.getSentAt()).isNotNull();
    }

    @Test
    void processNotification_claimed_usesClaimedSubject() throws Exception {
        service.processNotification(donation, DonationStatus.CLAIMED);

        verify(emailService).sendDonationThankYouEmail(
                eq(donation),
                eq("Your donation has been claimed!"));
    }

    @Test
    void processNotification_cancelled_usesCancelledSubject() throws Exception {
        service.processNotification(donation, DonationStatus.CANCELLED);

        verify(emailService).sendDonationThankYouEmail(
                eq(donation),
                eq("Your donation has been cancelled"));
    }

    @Test
    void processNotification_savesEntityTwice() throws Exception {
        service.processNotification(donation, DonationStatus.ACTIVE);

        verify(repository, times(2)).save(pendingEntity);
    }

    @Test
    void processNotification_setsUpdatedAt() throws Exception {
        service.processNotification(donation, DonationStatus.ACTIVE);

        assertThat(pendingEntity.getUpdatedAt()).isNotNull();
    }

    // ── processNotification — email failure ───────────────────────────────────

    @Test
    void processNotification_emailFails_setsStatusFailed() throws Exception {
        doThrow(new RuntimeException("SMTP down"))
                .when(emailService).sendDonationThankYouEmail(any(), anyString());

        service.processNotification(donation, DonationStatus.ACTIVE);

        assertThat(pendingEntity.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(pendingEntity.getFailureReason()).contains("SMTP down");
    }

    @Test
    void processNotification_emailFails_stillSavesSecondTime() throws Exception {
        doThrow(new RuntimeException("SMTP down"))
                .when(emailService).sendDonationThankYouEmail(any(), anyString());

        service.processNotification(donation, DonationStatus.ACTIVE);

        verify(repository, times(2)).save(pendingEntity);
    }

    // ── getNotificationsByDonationId ──────────────────────────────────────────

    @Test
    void getNotificationsByDonationId_returnsFromRepository() {
        when(repository.findByDonationId("don-002")).thenReturn(Flux.just(pendingEntity));

        List<?> result = service.getNotificationsByDonationId("don-002");

        assertThat(result).hasSize(1);
        verify(repository).findByDonationId("don-002");
    }

    @Test
    void getNotificationsByDonationId_noResults_returnsEmptyList() {
        when(repository.findByDonationId("unknown")).thenReturn(Flux.empty());

        assertThat(service.getNotificationsByDonationId("unknown")).isEmpty();
    }

    // ── getNotificationsByRecipient ───────────────────────────────────────────

    @Test
    void getNotificationsByRecipient_returnsFromRepository() {
        when(repository.findByRecipient("carol@example.com")).thenReturn(Flux.just(pendingEntity));

        List<?> result = service.getNotificationsByRecipient("carol@example.com");

        assertThat(result).hasSize(1);
        verify(repository).findByRecipient("carol@example.com");
    }

    @Test
    void getNotificationsByRecipient_noResults_returnsEmptyList() {
        when(repository.findByRecipient("nobody@example.com")).thenReturn(Flux.empty());

        assertThat(service.getNotificationsByRecipient("nobody@example.com")).isEmpty();
    }
}
