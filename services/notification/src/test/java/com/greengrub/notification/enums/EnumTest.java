package com.greengrub.notification.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class EnumTest {

    // ── DonationStatus ────────────────────────────────────────────────────────

    @Test
    void donationStatus_containsExpectedValues() {
        assertThat(DonationStatus.values())
                .containsExactlyInAnyOrder(DonationStatus.ACTIVE, DonationStatus.CLAIMED, DonationStatus.CANCELLED);
    }

    @Test
    void donationStatus_valueOf_active() {
        assertThat(DonationStatus.valueOf("ACTIVE")).isEqualTo(DonationStatus.ACTIVE);
    }

    @Test
    void donationStatus_valueOf_claimed() {
        assertThat(DonationStatus.valueOf("CLAIMED")).isEqualTo(DonationStatus.CLAIMED);
    }

    @Test
    void donationStatus_valueOf_cancelled() {
        assertThat(DonationStatus.valueOf("CANCELLED")).isEqualTo(DonationStatus.CANCELLED);
    }

    // ── NotificationStatus ────────────────────────────────────────────────────

    @Test
    void notificationStatus_containsExpectedValues() {
        assertThat(NotificationStatus.values())
                .containsExactlyInAnyOrder(NotificationStatus.PENDING, NotificationStatus.SENT, NotificationStatus.FAILED);
    }

    @Test
    void notificationStatus_valueOf_pending() {
        assertThat(NotificationStatus.valueOf("PENDING")).isEqualTo(NotificationStatus.PENDING);
    }

    // ── EmailTemplate ─────────────────────────────────────────────────────────

    @Test
    void emailTemplate_donationConfirmation_hasCorrectTemplate() {
        assertThat(EmailTemplate.DONATION_CONFIRMATION.getTemplate()).isEqualTo("donation.html");
    }

    @Test
    void emailTemplate_donationConfirmation_hasDescription() {
        assertThat(EmailTemplate.DONATION_CONFIRMATION.getDescription()).isNotBlank();
    }
}
