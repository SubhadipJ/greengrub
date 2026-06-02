package com.greengrub.image_service.repository;

import com.greengrub.image_service.entity.LocalImage;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

@Profile("local")
public interface LocalStorageImageRepository extends MongoRepository<LocalImage, String> {
    List<LocalImage> findByCreatorId(String creatorId);
    boolean deleteByImageId(String imageId);
}
