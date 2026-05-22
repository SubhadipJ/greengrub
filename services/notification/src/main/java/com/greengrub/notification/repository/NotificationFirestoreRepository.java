package com.greengrub.notification.repository;

import com.greengrub.notification.entity.NotificationEntity;
import org.springframework.context.annotation.Profile;
import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import reactor.core.publisher.Flux;

@Profile("k8s")
public interface NotificationFirestoreRepository extends FirestoreReactiveRepository<NotificationEntity> {

    Flux<NotificationEntity> findByDonationId(String donationId);

    Flux<NotificationEntity> findByRecipient(String recipient);
}
