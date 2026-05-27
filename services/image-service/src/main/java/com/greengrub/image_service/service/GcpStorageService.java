package com.greengrub.image_service.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.greengrub.image_service.exception.GcpUploadException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("k8s")
public class GcpStorageService {

    private final Storage storage;

    @Value("${gcp.bucket.name}")
    private String bucketName;

    @Value("${gcp.storage.base-url}")
    private String storageBaseUrl;

    public String uploadImage(byte[] imageBytes, String imageId, String creatorId, String contentType) {
        try {
            String fileName = creatorId + "/" + imageId + ".jpg";
            BlobId blobId = BlobId.of(bucketName, fileName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(contentType != null && !contentType.isBlank() ? contentType : "image/jpeg")
                    .build();
            storage.create(blobInfo, imageBytes);
            String publicUrl = storageBaseUrl + "/" + bucketName + "/" + fileName;
            log.info("Image uploaded to GCS: imageId={} url={}", imageId, publicUrl);
            return publicUrl;
        } catch (Exception e) {
            log.error("GCS upload failed for imageId={} creatorId={}", imageId, creatorId, e);
            throw new GcpUploadException(imageId, e);
        }
    }
}
