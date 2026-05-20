package com.greengrub.notification.service;

import com.greengrub.notification.dto.Donation;
import com.greengrub.notification.enums.DonationStatus;

import java.util.List;

public interface NotificationService {

    void processNotification(Donation donation, DonationStatus eventType);

    List<?> getNotificationsByDonationId(String donationId);

    List<?> getNotificationsByRecipient(String recipient);
}
