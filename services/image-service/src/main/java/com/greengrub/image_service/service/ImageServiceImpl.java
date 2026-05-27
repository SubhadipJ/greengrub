package com.greengrub.image_service.service;

import com.google.cloud.Timestamp;
import com.google.cloud.spring.data.firestore.FirestoreTemplate;
import com.google.protobuf.ByteString;
import com.greengrub.image_service.entity.Image;
import com.greengrub.image_service.enumeration.CreatorType;
import com.greengrub.image_service.exception.GcpUploadException;
import com.greengrub.image_service.exception.ImageNotFoundException;
import com.greengrub.image_service.exception.ImageStorageException;
import com.greengrub.image_service.exception.InvalidImageRequestException;
import com.greengrub.image_service.mapper.ImageMapper;
import com.greengrub.proto.image.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.grpc.stub.StreamObserver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.context.annotation.Profile;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@GrpcService
@AllArgsConstructor
@Profile("k8s")
public class ImageServiceImpl extends ImageServiceGrpc.ImageServiceImplBase {

    private final FirestoreTemplate firestoreTemplate;
    private final GcpStorageService gcpStorageService;

    @Override
    public void uploadImages(UploadImagesRequest request, StreamObserver<UploadImagesResponse> responseObserver) {
        String creatorId = request.getCreatorId();
        String creatorType = request.getCreatorType().toString();
        List<String> messages = new ArrayList<>();
        log.info("Received upload request for creatorId: {} | {} images", creatorId, request.getImageDataCount());

        for (ByteString byteString : request.getImageDataList()) {
            byte[] imageBytes = byteString.toByteArray();
            if (imageBytes.length == 0) {
                continue;
            }
            String imageId = UUID.randomUUID().toString();

            // Upload to GCP Cloud Storage (with retry + circuit breaker + timeout)
            String imageUrl = uploadToGcp(imageBytes, imageId, creatorId).join();

            // Save metadata to Firestore (with retry + circuit breaker)
            Image entity = Image.builder()
                    .imageId(imageId)
                    .creatorId(creatorId)
                    .creatorType(CreatorType.valueOf(creatorType))
                    .fileName(request.getFileName())
                    .contentType(request.getContentType())
                    .imageUrl(imageUrl)
                    .createdDate(Timestamp.now())
                    .build();

            saveToFirestore(entity);
            messages.add(request.getFileName() + " | got stored with id : " + imageId);
            log.info("Successfully uploaded image: {} | URL: {}", imageId, imageUrl);
        }

        UploadImagesResponse response = UploadImagesResponse.newBuilder()
                .addAllMessage(messages)
                .setTotalImageCount(String.valueOf(messages.size()))
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getImagesByCreator(GetImagesByCreatorRequest request, StreamObserver<GetImagesByCreatorResponse> responseObserver) {
        String creatorId = request.getCreatorId();
        log.info("Fetching images for creatorId: {}", creatorId);

        List<Image> entities = findByCreatorId(creatorId);

        List<com.greengrub.proto.image.Image> protoImages = entities.stream()
                .map(ImageMapper::getProtoImageFromServiceImage)
                .toList();

        GetImagesByCreatorResponse response = GetImagesByCreatorResponse.newBuilder()
                .addAllImages(protoImages)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void deleteImagesByImageId(ImageByImageIdRequest request, StreamObserver<DeleteImageByImageIdResponse> responseObserver) {
        if (request.getImageId().trim().isEmpty()) {
            throw new InvalidImageRequestException("Image ID cannot be empty");
        }

        Image image = findByIdOrNull(request.getImageId());
        boolean deleted = false;
        if (image != null) {
            deleteFromFirestore(image);
            deleted = true;
        }

        String responseMessage = (image != null)
                ? "Successfully deleted image: " + image.getFileName()
                : "Image not found";

        DeleteImageByImageIdResponse response = DeleteImageByImageIdResponse.newBuilder()
                .setMessage(deleted ? responseMessage : "Image not found")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getImageByImageId(ImageByImageIdRequest request, StreamObserver<GetImagesByCreatorResponse> responseObserver) {
        String imageId = request.getImageId();
        if (imageId.trim().isEmpty()) {
            throw new InvalidImageRequestException("Image ID cannot be empty");
        }

        Image image = findById(imageId);

        com.greengrub.proto.image.Image protoImage = ImageMapper.getProtoImageFromServiceImage(image);

        GetImagesByCreatorResponse response = GetImagesByCreatorResponse.newBuilder()
                .addImages(protoImage)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    // --- Resilience-wrapped GCP Cloud Storage call ---

    @TimeLimiter(name = "gcpUploadLimiter")
    @Retry(name = "gcpRetry")
    @CircuitBreaker(name = "gcpBreaker")
    private CompletableFuture<String> uploadToGcp(byte[] imageBytes, String imageId, String creatorId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return gcpStorageService.uploadImage(imageBytes, imageId, creatorId, null);
            } catch (GcpUploadException e) {
                log.warn("GCP upload failed for imageId: {} - will retry", imageId);
                throw e;
            } catch (Exception e) {
                log.warn("GCP upload failed for imageId: {} - will retry", imageId);
                throw new GcpUploadException(imageId, e);
            }
        });
    }

    // --- Resilience-wrapped Firestore calls ---

    @Retry(name = "firestoreRetry")
    @CircuitBreaker(name = "firestoreBreaker")
    private void saveToFirestore(Image entity) {
        try {
            firestoreTemplate.save(entity).block();
        } catch (Exception e) {
            log.warn("Firestore save failed for imageId: {} - will retry", entity.getImageId());
            throw new ImageStorageException("Firestore save failed for imageId: " + entity.getImageId(), e, true);
        }
    }

    @Retry(name = "firestoreRetry")
    @CircuitBreaker(name = "firestoreBreaker")
    private List<Image> findByCreatorId(String creatorId) {
        try {
            return firestoreTemplate.findAll(Image.class)
                    .filter(image -> creatorId.equals(image.getCreatorId()))
                    .collectList()
                    .block();
        } catch (Exception e) {
            log.warn("Firestore findByCreatorId failed for creatorId: {} - will retry", creatorId);
            throw new ImageStorageException("Firestore query failed for creatorId: " + creatorId, e, true);
        }
    }

    @Retry(name = "firestoreRetry")
    @CircuitBreaker(name = "firestoreBreaker")
    private Image findById(String imageId) {
        try {
            Image image = firestoreTemplate.findById(Mono.just(imageId), Image.class).block();
            if (image == null) {
                throw new ImageNotFoundException(imageId);
            }
            return image;
        } catch (ImageNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Firestore findById failed for imageId: {} - will retry", imageId);
            throw new ImageStorageException("Firestore query failed for imageId: " + imageId, e, true);
        }
    }

    @Retry(name = "firestoreRetry")
    @CircuitBreaker(name = "firestoreBreaker")
    private Image findByIdOrNull(String imageId) {
        try {
            return firestoreTemplate.findById(Mono.just(imageId), Image.class).block();
        } catch (Exception e) {
            log.warn("Firestore findById failed for imageId: {} - will retry", imageId);
            throw new ImageStorageException("Firestore query failed for imageId: " + imageId, e, true);
        }
    }

    private void deleteFromFirestore(Image image) {
        try {
            firestoreTemplate.delete(Mono.just(image)).block();
        } catch (Exception e) {
            log.error("Firestore delete failed for imageId: {}", image.getImageId(), e);
            throw new ImageStorageException("Firestore delete failed for imageId: " + image.getImageId(), e, false);
        }
    }
}
