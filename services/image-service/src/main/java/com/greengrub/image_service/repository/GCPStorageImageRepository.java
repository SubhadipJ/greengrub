package com.greengrub.image_service.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.greengrub.image_service.entity.Image;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface GCPStorageImageRepository extends FirestoreReactiveRepository<Image> {
    Flux<Image> findByCreatorId(String creatorId);
    Mono<Long> deleteByImageId(String imageId);
}
