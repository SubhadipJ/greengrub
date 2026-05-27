package com.greengrub.notification.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.greengrub.notification.dto.Customer;
import com.greengrub.notification.dto.DonatedItem;
import com.greengrub.notification.dto.Donation;
import com.greengrub.notification.entity.NotificationDocument;
import com.greengrub.notification.entity.NotificationEntity;
import com.greengrub.notification.enums.DonationStatus;
import com.greengrub.notification.enums.NotificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class NotificationMapperTest {

    private NotificationMapper mapper;
    private Donation donation;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mapper = new NotificationMapper(objectMapper);

        Customer customer = new Customer("cust-001", "John", "Doe", "john@example.com", "555-0100");
        DonatedItem item = new DonatedItem("Rice", 2, "kg", "Grains");
        donation = new Donation(
                "don-001", "John Doe", "john@example.com",
                new BigDecimal("100.00"),
                LocalDateTime.of(2024, 3, 15, 10, 0, 0),
                customer, "GreenGrub Org", "ACTIVE",
                List.of(item)
        );
    }

    // ── toDocument ────────────────────────────────────────────────────────────

    @Test
    void toDocument_mapsAllFields() {
        NotificationDocument doc = mapper.toDocument(donation, DonationStatus.ACTIVE);

        assertThat(doc.getDonationId()).isEqualTo("don-001");
        assertThat(doc.getEventType()).isEqualTo(DonationStatus.ACTIVE);
        assertThat(doc.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(doc.getRecipient()).isEqualTo("john@example.com");
    }

    @Test
    void toDocument_payloadIsValidJson() {
        NotificationDocument doc = mapper.toDocument(donation, DonationStatus.ACTIVE);

        assertThat(doc.getPayload()).isNotBlank();
        assertThat(doc.getPayload()).contains("don-001");
        assertThat(doc.getPayload()).contains("john@example.com");
    }

    @Test
    void toDocument_statusIsAlwaysPending() {
        NotificationDocument docActive = mapper.toDocument(donation, DonationStatus.ACTIVE);
        NotificationDocument docClaimed = mapper.toDocument(donation, DonationStatus.CLAIMED);
        NotificationDocument docCancelled = mapper.toDocument(donation, DonationStatus.CANCELLED);

        assertThat(docActive.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(docClaimed.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(docCancelled.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void toDocument_timestampsAreSet() {
        NotificationDocument doc = mapper.toDocument(donation, DonationStatus.ACTIVE);

        assertThat(doc.getNotificationTime()).isNotNull();
        assertThat(doc.getCreatedAt()).isNotNull();
        assertThat(doc.getUpdatedAt()).isNotNull();
    }

    @Test
    void toDocument_claimedEventType() {
        NotificationDocument doc = mapper.toDocument(donation, DonationStatus.CLAIMED);
        assertThat(doc.getEventType()).isEqualTo(DonationStatus.CLAIMED);
    }

    @Test
    void toDocument_cancelledEventType() {
        NotificationDocument doc = mapper.toDocument(donation, DonationStatus.CANCELLED);
        assertThat(doc.getEventType()).isEqualTo(DonationStatus.CANCELLED);
    }

    // ── toEntity ──────────────────────────────────────────────────────────────

    @Test
    void toEntity_mapsAllFields() {
        NotificationEntity entity = mapper.toEntity(donation, DonationStatus.ACTIVE);

        assertThat(entity.getDonationId()).isEqualTo("don-001");
        assertThat(entity.getEventType()).isEqualTo(DonationStatus.ACTIVE);
        assertThat(entity.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(entity.getRecipient()).isEqualTo("john@example.com");
    }

    @Test
    void toEntity_payloadIsValidJson() {
        NotificationEntity entity = mapper.toEntity(donation, DonationStatus.ACTIVE);

        assertThat(entity.getPayload()).isNotBlank();
        assertThat(entity.getPayload()).contains("don-001");
    }

    @Test
    void toEntity_timestampsAreSet() {
        NotificationEntity entity = mapper.toEntity(donation, DonationStatus.CLAIMED);

        assertThat(entity.getNotificationTime()).isNotNull();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    void toEntity_statusIsAlwaysPending() {
        NotificationEntity entity = mapper.toEntity(donation, DonationStatus.CANCELLED);
        assertThat(entity.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }
}
