package com.greengrub.notification.consumer;

import com.greengrub.notification.dto.Customer;
import com.greengrub.notification.dto.DonatedItem;
import com.greengrub.notification.dto.Donation;
import com.greengrub.notification.enums.DonationStatus;
import com.greengrub.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationConsumer consumer;

    private Donation activeDonation;
    private Donation claimedDonation;
    private Donation cancelledDonation;
    private Donation nullStatusDonation;
    private Donation unknownStatusDonation;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer("cust-001", "Alice", "Green", "alice@example.com", "555-0300");
        List<DonatedItem> items = List.of(new DonatedItem("Corn", 3, "kg", "Vegetables"));

        activeDonation = new Donation(
                "don-001", "Alice Green", "alice@example.com",
                new BigDecimal("75.00"),
                LocalDateTime.of(2024, 4, 10, 14, 0, 0),
                customer, "GreenGrub", "ACTIVE", items
        );
        claimedDonation = new Donation(
                "don-002", "Alice Green", "alice@example.com",
                new BigDecimal("75.00"),
                LocalDateTime.of(2024, 4, 10, 14, 0, 0),
                customer, "GreenGrub", "CLAIMED", items
        );
        cancelledDonation = new Donation(
                "don-003", "Alice Green", "alice@example.com",
                new BigDecimal("75.00"),
                LocalDateTime.of(2024, 4, 10, 14, 0, 0),
                customer, "GreenGrub", "CANCELLED", items
        );
        nullStatusDonation = new Donation(
                "don-004", "Alice Green", "alice@example.com",
                new BigDecimal("75.00"),
                LocalDateTime.of(2024, 4, 10, 14, 0, 0),
                customer, "GreenGrub", null, items
        );
        unknownStatusDonation = new Donation(
                "don-005", "Alice Green", "alice@example.com",
                new BigDecimal("75.00"),
                LocalDateTime.of(2024, 4, 10, 14, 0, 0),
                customer, "GreenGrub", "PENDING_REVIEW", items
        );
    }

    @Test
    void handleDonationEvent_activeStatus_callsProcessWithActive() {
        consumer.handleDonationEvent(activeDonation);

        ArgumentCaptor<DonationStatus> captor = ArgumentCaptor.forClass(DonationStatus.class);
        verify(notificationService).processNotification(eq(activeDonation), captor.capture());
        assertThat(captor.getValue()).isEqualTo(DonationStatus.ACTIVE);
    }

    @Test
    void handleDonationEvent_claimedStatus_callsProcessWithClaimed() {
        consumer.handleDonationEvent(claimedDonation);

        ArgumentCaptor<DonationStatus> captor = ArgumentCaptor.forClass(DonationStatus.class);
        verify(notificationService).processNotification(eq(claimedDonation), captor.capture());
        assertThat(captor.getValue()).isEqualTo(DonationStatus.CLAIMED);
    }

    @Test
    void handleDonationEvent_cancelledStatus_callsProcessWithCancelled() {
        consumer.handleDonationEvent(cancelledDonation);

        ArgumentCaptor<DonationStatus> captor = ArgumentCaptor.forClass(DonationStatus.class);
        verify(notificationService).processNotification(eq(cancelledDonation), captor.capture());
        assertThat(captor.getValue()).isEqualTo(DonationStatus.CANCELLED);
    }

    @Test
    void handleDonationEvent_nullStatus_defaultsToActive() {
        consumer.handleDonationEvent(nullStatusDonation);

        ArgumentCaptor<DonationStatus> captor = ArgumentCaptor.forClass(DonationStatus.class);
        verify(notificationService).processNotification(eq(nullStatusDonation), captor.capture());
        assertThat(captor.getValue()).isEqualTo(DonationStatus.ACTIVE);
    }

    @Test
    void handleDonationEvent_unknownStatus_defaultsToActive() {
        consumer.handleDonationEvent(unknownStatusDonation);

        ArgumentCaptor<DonationStatus> captor = ArgumentCaptor.forClass(DonationStatus.class);
        verify(notificationService).processNotification(eq(unknownStatusDonation), captor.capture());
        assertThat(captor.getValue()).isEqualTo(DonationStatus.ACTIVE);
    }

    @Test
    void handleDonationEvent_passesExactDonationObject() {
        consumer.handleDonationEvent(activeDonation);

        verify(notificationService).processNotification(eq(activeDonation), any(DonationStatus.class));
    }

    @Test
    void handleDonationEvent_processNotificationCalledOnce() {
        consumer.handleDonationEvent(activeDonation);

        verify(notificationService, times(1)).processNotification(any(), any());
    }

    @Test
    void handleDonationEvent_serviceThrows_propagatesException() {
        doThrow(new RuntimeException("service down"))
                .when(notificationService).processNotification(any(), any());

        assertThatThrownBy(() -> consumer.handleDonationEvent(activeDonation))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("service down");
    }
}
