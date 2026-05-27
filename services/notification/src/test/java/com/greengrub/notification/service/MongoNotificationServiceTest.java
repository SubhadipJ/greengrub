package com.greengrub.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greengrub.notification.dto.Customer;
import com.greengrub.notification.dto.DonatedItem;
import com.greengrub.notification.dto.Donation;
import com.greengrub.notification.email.EmailService;
import com.greengrub.notification.entity.NotificationDocument;
import com.greengrub.notification.enums.DonationStatus;
import com.greengrub.notification.enums.NotificationStatus;
import com.greengrub.notification.mapper.NotificationMapper;
import com.greengrub.notification.repository.NotificationMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MongoNotificationServiceTest {

    @Mock
    private NotificationMongoRepository repository;

    @Mock
    private NotificationMapper mapper;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private MongoNotificationService service;

    private Donation donation;
    private NotificationDocument pendingDoc;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer("cust-001", "Bob", "Jones", "bob@example.com", "555-0400");
        donation = new Donation(
                "don-001", "Bob Jones", "bob@example.com",
                new BigDecimal("150.00"),
                LocalDateTime.of(2024, 5, 20, 11, 0, 0),
                customer, "GreenGrub Org", "ACTIVE",
                List.of(new DonatedItem("Beans", 4, "kg", "Legumes"))
        );

        pendingDoc = NotificationDocument.builder()
                .id("notif-001")
                .donationId("don-001")
                .status(NotificationStatus.PENDING)
                .recipient("bob@example.com")
                .build();

        when(mapper.toDocument(any(Donation.class), any(DonationStatus.class))).thenReturn(pendingDoc);
        when(repository.save(any(NotificationDocument.class))).thenReturn(pendingDoc);
    }

    // ── processNotification — success ─────────────────────────────────────────

    @Test
    void processNotification_active_sendsEmailAndSetsStatusSent() throws Exception {
        service.processNotification(donation, DonationStatus.ACTIVE);

        verify(emailService).sendDonationThankYouEmail(
                eq(donation),
                eq("Thank you for changing lives with your donation!"));
        assertThat(pendingDoc.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(pendingDoc.getSentAt()).isNotNull();
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
    void processNotification_savesDocumentTwice() throws Exception {
        service.processNotification(donation, DonationStatus.ACTIVE);

        verify(repository, times(2)).save(pendingDoc);
    }

    @Test
    void processNotification_setsUpdatedAt() throws Exception {
        service.processNotification(donation, DonationStatus.ACTIVE);

        assertThat(pendingDoc.getUpdatedAt()).isNotNull();
    }

    // ── processNotification — email failure ───────────────────────────────────

    @Test
    void processNotification_emailFails_setsStatusFailed() throws Exception {
        doThrow(new RuntimeException("SMTP down"))
                .when(emailService).sendDonationThankYouEmail(any(), anyString());

        service.processNotification(donation, DonationStatus.ACTIVE);

        assertThat(pendingDoc.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(pendingDoc.getFailureReason()).contains("SMTP down");
    }

    @Test
    void processNotification_emailFails_stillSavesSecondTime() throws Exception {
        doThrow(new RuntimeException("SMTP down"))
                .when(emailService).sendDonationThankYouEmail(any(), anyString());

        service.processNotification(donation, DonationStatus.ACTIVE);

        verify(repository, times(2)).save(pendingDoc);
    }

    // ── getNotificationsByDonationId ──────────────────────────────────────────

    @Test
    void getNotificationsByDonationId_returnsFromRepository() {
        when(repository.findByDonationId("don-001")).thenReturn(List.of(pendingDoc));

        List<?> result = service.getNotificationsByDonationId("don-001");

        assertThat(result).hasSize(1);
        verify(repository).findByDonationId("don-001");
    }

    @Test
    void getNotificationsByDonationId_noResults_returnsEmptyList() {
        when(repository.findByDonationId("unknown")).thenReturn(List.of());

        assertThat(service.getNotificationsByDonationId("unknown")).isEmpty();
    }

    // ── getNotificationsByRecipient ───────────────────────────────────────────

    @Test
    void getNotificationsByRecipient_returnsFromRepository() {
        when(repository.findByRecipient("bob@example.com")).thenReturn(List.of(pendingDoc));

        List<?> result = service.getNotificationsByRecipient("bob@example.com");

        assertThat(result).hasSize(1);
        verify(repository).findByRecipient("bob@example.com");
    }

    @Test
    void getNotificationsByRecipient_noResults_returnsEmptyList() {
        when(repository.findByRecipient("nobody@example.com")).thenReturn(List.of());

        assertThat(service.getNotificationsByRecipient("nobody@example.com")).isEmpty();
    }
}
