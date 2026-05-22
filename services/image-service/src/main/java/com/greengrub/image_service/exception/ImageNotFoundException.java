package com.greengrub.image_service.exception;

public class ImageNotFoundException extends RuntimeException {

    public ImageNotFoundException(String imageId) {
        super("Image not found with id: " + imageId);
    }
}
