package com.greengrub.notification.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ExceptionTest {

    // ── NotificationNotFoundException ────────────────────────────────────────

    @Test
    void notificationNotFoundException_messageContainsId() {
        NotificationNotFoundException ex = new NotificationNotFoundException("notif-123");
        assertThat(ex.getMessage()).isEqualTo("Notification not found with id: notif-123");
    }

    @Test
    void notificationNotFoundException_isRuntimeException() {
        assertThat(new NotificationNotFoundException("x"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void notificationNotFoundException_noCause() {
        assertThat(new NotificationNotFoundException("y").getCause()).isNull();
    }

    // ── NotificationProcessingException ──────────────────────────────────────

    @Test
    void notificationProcessingException_preservesMessageAndCause() {
        Throwable cause = new RuntimeException("kafka error");
        NotificationProcessingException ex = new NotificationProcessingException("processing failed", cause);

        assertThat(ex.getMessage()).isEqualTo("processing failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void notificationProcessingException_isRuntimeException() {
        assertThat(new NotificationProcessingException("msg", new RuntimeException()))
                .isInstanceOf(RuntimeException.class);
    }
}
