package com.greengrub.image_service.exception;

public class GcpUploadException extends ImageStorageException {

    public GcpUploadException(String imageId, Throwable cause) {
        super("Failed to upload image to GCP Cloud Storage for imageId: " + imageId, cause, true);
    }

    public GcpUploadException(String message) {
        super(message, true);
    }
}
