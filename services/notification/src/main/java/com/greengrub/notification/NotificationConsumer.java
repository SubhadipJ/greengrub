package com.greengrub.notification;

import com.greengrub.notification.donation.Donation;
import com.greengrub.notification.email.EmailService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@EnableKafka
@AllArgsConstructor
public class NotificationConsumer {
    private final EmailService emailService;

    @KafkaListener(topics = "donation-topic",groupId = "donationGroup")
    public void handleDonationCreated(Donation event) {
        log.info("Received donation event → ID: {}, Donor: {}, Items: {}",
                event.donationId(), event.donorName(), event.items().size());

        emailService.sendDonationThankYouEmail(event,"Thank you for changing lives with your donation!");
    }
}
