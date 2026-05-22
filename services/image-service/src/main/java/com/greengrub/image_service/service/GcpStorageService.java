package com.greengrub.image_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("k8s")
public class GcpStorageService {

//    private final Storage storage;   // Auto-configured by Spring Cloud GCP
//
//    @Value("${gcp.bucket.name}")
//    private String bucketName;
//
//    public String uploadImage(byte[] imageBytes, String imageId, String creatorId) {
//        try {
//            String fileName = creatorId + "/" + imageId + "-" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
//            BlobId blobId = BlobId.of(bucketName, fileName);
//            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("image/jpeg").build();
//            storage.create(blobInfo, imageBytes);
//            String publicUrl = "https://storage.googleapis.com/" + bucketName + "/" + fileName;
//            log.info("Image uploaded to GCP: {}", publicUrl);
//            return publicUrl;
//        } catch (Exception e) {
//            log.error("Failed to upload image to GCP for imageId: {}", imageId, e);
//            throw new RuntimeException("Failed to upload image to cloud storage", e);
//        }
//    }
}
