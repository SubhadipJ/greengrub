package com.greengrub.notification.controller;

import com.greengrub.notification.entity.NotificationDocument;
import com.greengrub.notification.enums.DonationStatus;
import com.greengrub.notification.enums.NotificationStatus;
import com.greengrub.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController controller;

    private NotificationDocument sampleDoc() {
        return NotificationDocument.builder()
                .id("notif-001")
                .donationId("don-001")
                .eventType(DonationStatus.ACTIVE)
                .status(NotificationStatus.SENT)
                .recipient("user@example.com")
                .build();
    }

    // ── getByDonationId ───────────────────────────────────────────────────────

    @Test
    void getByDonationId_returnsOkWithList() {
        doReturn(List.of(sampleDoc())).when(notificationService).getNotificationsByDonationId("don-001");

        ResponseEntity<List<?>> response = controller.getByDonationId("don-001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void getByDonationId_emptyList_returnsOkWithEmptyBody() {
        doReturn(List.of()).when(notificationService).getNotificationsByDonationId("missing");

        ResponseEntity<List<?>> response = controller.getByDonationId("missing");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getByDonationId_multipleResults_returnsAll() {
        doReturn(List.of(sampleDoc(), sampleDoc())).when(notificationService).getNotificationsByDonationId("don-001");

        ResponseEntity<List<?>> response = controller.getByDonationId("don-001");

        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void getByDonationId_delegatesToService() {
        doReturn(List.of()).when(notificationService).getNotificationsByDonationId("don-42");

        controller.getByDonationId("don-42");

        verify(notificationService).getNotificationsByDonationId("don-42");
    }

    // ── getByRecipient ────────────────────────────────────────────────────────

    @Test
    void getByRecipient_returnsOkWithList() {
        doReturn(List.of(sampleDoc())).when(notificationService).getNotificationsByRecipient("user@example.com");

        ResponseEntity<List<?>> response = controller.getByRecipient("user@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void getByRecipient_emptyList_returnsOkWithEmptyBody() {
        doReturn(List.of()).when(notificationService).getNotificationsByRecipient("nobody@example.com");

        ResponseEntity<List<?>> response = controller.getByRecipient("nobody@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getByRecipient_delegatesToService() {
        doReturn(List.of()).when(notificationService).getNotificationsByRecipient("test@test.com");

        controller.getByRecipient("test@test.com");

        verify(notificationService).getNotificationsByRecipient("test@test.com");
    }
}
