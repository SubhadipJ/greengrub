package com.greengrub.image_service.repository;


import com.greengrub.image_service.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GCPStorageImageRepository extends JpaRepository<Image, String> {
    List<Image> findByCreatorId(String creatorId);
    boolean deleteByImageId(String imageId);
}
