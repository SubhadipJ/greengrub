package com.greengrub.image_service.exception;

public class ImageStorageException extends RuntimeException {

    private final boolean retryable;

    public ImageStorageException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public ImageStorageException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
