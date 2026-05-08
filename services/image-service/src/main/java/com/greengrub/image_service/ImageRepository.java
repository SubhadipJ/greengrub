package com.greengrub.image_service;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImageRepository extends JpaRepository<Image, String> {
    List<Image> findByCreatorId(String creatorId);
}
