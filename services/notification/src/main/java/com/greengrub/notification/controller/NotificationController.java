package com.greengrub.notification.controller;

import com.greengrub.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/donation/{donationId}")
    public ResponseEntity<List<?>> getByDonationId(@PathVariable String donationId) {
        return ResponseEntity.ok(notificationService.getNotificationsByDonationId(donationId));
    }

    @GetMapping("/recipient/{email}")
    public ResponseEntity<List<?>> getByRecipient(@PathVariable String email) {
        return ResponseEntity.ok(notificationService.getNotificationsByRecipient(email));
    }
}
