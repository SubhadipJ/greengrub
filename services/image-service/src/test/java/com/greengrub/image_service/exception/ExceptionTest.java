package com.greengrub.image_service.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ExceptionTest {

    // ── ImageStorageException ────────────────────────────────────────────────

    @Test
    void imageStorageException_withCause_isRetryable() {
        Throwable cause = new RuntimeException("db down");
        ImageStorageException ex = new ImageStorageException("save failed", cause, true);

        assertThat(ex.getMessage()).isEqualTo("save failed");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.isRetryable()).isTrue();
    }

    @Test
    void imageStorageException_withCause_notRetryable() {
        Throwable cause = new RuntimeException("constraint");
        ImageStorageException ex = new ImageStorageException("constraint violation", cause, false);

        assertThat(ex.isRetryable()).isFalse();
    }

    @Test
    void imageStorageException_withoutCause_isRetryable() {
        ImageStorageException ex = new ImageStorageException("transient error", true);

        assertThat(ex.getMessage()).isEqualTo("transient error");
        assertThat(ex.getCause()).isNull();
        assertThat(ex.isRetryable()).isTrue();
    }

    @Test
    void imageStorageException_withoutCause_notRetryable() {
        ImageStorageException ex = new ImageStorageException("permanent error", false);
        assertThat(ex.isRetryable()).isFalse();
    }

    @Test
    void imageStorageException_isRuntimeException() {
        assertThat(new ImageStorageException("msg", true))
                .isInstanceOf(RuntimeException.class);
    }

    // ── GcpUploadException ───────────────────────────────────────────────────

    @Test
    void gcpUploadException_withImageIdAndCause_containsImageId() {
        Throwable cause = new RuntimeException("network error");
        GcpUploadException ex = new GcpUploadException("img-001", cause);

        assertThat(ex.getMessage()).contains("img-001");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.isRetryable()).isTrue();
    }

    @Test
    void gcpUploadException_withMessage_isRetryable() {
        GcpUploadException ex = new GcpUploadException("custom message");

        assertThat(ex.getMessage()).isEqualTo("custom message");
        assertThat(ex.isRetryable()).isTrue();
    }

    @Test
    void gcpUploadException_extendsImageStorageException() {
        assertThat(new GcpUploadException("msg"))
                .isInstanceOf(ImageStorageException.class);
    }

    // ── ImageNotFoundException ───────────────────────────────────────────────

    @Test
    void imageNotFoundException_containsId() {
        ImageNotFoundException ex = new ImageNotFoundException("abc-123");

        assertThat(ex.getMessage()).isEqualTo("Image not found with id: abc-123");
    }

    @Test
    void imageNotFoundException_isRuntimeException() {
        assertThat(new ImageNotFoundException("x"))
                .isInstanceOf(RuntimeException.class);
    }

    // ── InvalidImageRequestException ─────────────────────────────────────────

    @Test
    void invalidImageRequestException_preservesMessage() {
        InvalidImageRequestException ex = new InvalidImageRequestException("ID cannot be empty");

        assertThat(ex.getMessage()).isEqualTo("ID cannot be empty");
    }

    @Test
    void invalidImageRequestException_isRuntimeException() {
        assertThat(new InvalidImageRequestException("msg"))
                .isInstanceOf(RuntimeException.class);
    }
}
