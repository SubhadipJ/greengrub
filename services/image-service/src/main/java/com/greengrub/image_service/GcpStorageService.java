package com.greengrub.image_service;

//import com.google.api.services.storage.Storage;
//import com.google.cloud.storage.BlobId;
//import com.google.cloud.storage.BlobInfo;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GcpStorageService {

//    private final Storage storage;   // Auto-configured by Spring Cloud GCP
//
//    @Value("${gcp.bucket.name}")
//    private String bucketName;
//
//    /**
//     * Uploads image to GCP Cloud Storage and returns public URL
//     */
//    public String uploadImage(byte[] imageBytes, String imageId, String creatorId) {
//        try {
//            // Create a meaningful filename: creatorId/imageId-originalName.jpg
//            String fileName = creatorId + "/" + imageId + "-" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
//
//            BlobId blobId = BlobId.of(bucketName, fileName);
//            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
//                    .setContentType("image/jpeg")           // You can make this dynamic later
//                    .build();
//
//            // Upload the bytes
//            //storage.create(blobInfo, imageBytes);
//
//            // Return public URL (make sure your bucket allows public read)
//            String publicUrl = "https://storage.googleapis.com/" + bucketName + "/" + fileName;
//
//            log.info("Image uploaded to GCP: {}", publicUrl);
//            return publicUrl;
//
//        } catch (Exception e) {
//            log.error("Failed to upload image to GCP for imageId: {}", imageId, e);
//            throw new RuntimeException("Failed to upload image to cloud storage", e);
//        }
//    }
}
