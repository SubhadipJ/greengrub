package com.greengrub.image_service.exception;

public class InvalidImageRequestException extends RuntimeException {

    public InvalidImageRequestException(String message) {
        super(message);
    }
}
