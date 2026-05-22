package com.greengrub.notification.repository;

import com.greengrub.notification.entity.NotificationDocument;
import com.greengrub.notification.enums.NotificationStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

@Profile("local")
public interface NotificationMongoRepository extends MongoRepository<NotificationDocument, String> {

    List<NotificationDocument> findByDonationId(String donationId);

    List<NotificationDocument> findByStatus(NotificationStatus status);

    List<NotificationDocument> findByRecipient(String recipient);
}
