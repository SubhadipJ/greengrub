package com.greengrub.notification.exception;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(String id) {
        super("Notification not found with id: " + id);
    }
}
