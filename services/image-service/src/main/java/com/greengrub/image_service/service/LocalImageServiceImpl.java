package com.greengrub.image_service.service;

import com.google.protobuf.ByteString;
import com.greengrub.image_service.entity.LocalImage;
import com.greengrub.image_service.enumeration.CreatorType;
import com.greengrub.image_service.exception.ImageNotFoundException;
import com.greengrub.image_service.exception.ImageStorageException;
import com.greengrub.image_service.exception.InvalidImageRequestException;
import com.greengrub.image_service.mapper.ImageMapper;
import com.greengrub.image_service.repository.LocalStorageImageRepository;
import com.greengrub.proto.image.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.stub.StreamObserver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.context.annotation.Profile;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@AllArgsConstructor
@GrpcService
@Profile("local")
public class LocalImageServiceImpl extends ImageServiceGrpc.ImageServiceImplBase {

    private final LocalStorageImageRepository localStorageImageRepository;

    @Override
    public void uploadImages(UploadImagesRequest request, StreamObserver<UploadImagesResponse> responseObserver) {
        String creatorId = request.getCreatorId();
        String creatorType = request.getCreatorType().toString();
        List<String> messages = new ArrayList<>();

        log.info("Received upload request for creatorId in Local: {} | {} images", creatorId, request.getImageDataCount());

        for (ByteString byteString : request.getImageDataList()) {
            byte[] imageBytes = byteString.toByteArray();
            if (imageBytes.length == 0) {
                continue;
            }
            String imageId = UUID.randomUUID().toString();
            LocalImage entity = LocalImage.builder()
                .imageId(imageId)
                .creatorId(creatorId)
                .creatorType(CreatorType.valueOf(creatorType))
                .fileName(request.getFileName())
                .contentType(request.getContentType())
                .imageData(imageBytes)
                .createdDate(LocalDateTime.now())
                .build();

            saveToMongo(entity);
            messages.add(imageId);
            log.info("Successfully uploaded image in Local: {} | fileName: {}", imageId, entity.getFileName());
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
        log.info("Fetching images for creatorId in Local: {}", creatorId);

        List<LocalImage> entities = findByCreatorId(creatorId);

        List<com.greengrub.proto.image.Image> protoImages = entities.stream()
                .map(ImageMapper::getProtoImageFromLocalImage)
                .toList();

        GetImagesByCreatorResponse response = GetImagesByCreatorResponse.newBuilder()
                .addAllImages(protoImages)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void deleteImagesByImageId(ImageByImageIdRequest request, StreamObserver<DeleteImageByImageIdResponse> responseObserver) {
        if (request.getImageId().trim().isEmpty()) {
            throw new InvalidImageRequestException("Image ID cannot be empty");
        }

        LocalImage localImage = localStorageImageRepository.findById(request.getImageId()).orElse(null);
        boolean deleted = localStorageImageRepository.deleteByImageId(request.getImageId());

        String responseMessage = (localImage != null)
                ? "Successfully deleted image in Local: " + localImage.getFileName()
                : "Image deleted successfully";

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

        LocalImage localImage = findById(imageId);

        Image protoImage = ImageMapper.getProtoImageFromLocalImage(localImage);

        GetImagesByCreatorResponse response = GetImagesByCreatorResponse.newBuilder()
                .addImages(protoImage)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    // --- Resilience-wrapped repository calls ---

    @Retry(name = "mongoRetry")
    @CircuitBreaker(name = "mongoBreaker")
    private void saveToMongo(LocalImage entity) {
        try {
            localStorageImageRepository.save(entity);
        } catch (Exception e) {
            log.warn("MongoDB save failed for imageId: {} - will retry", entity.getImageId());
            throw new ImageStorageException("MongoDB save failed for imageId: " + entity.getImageId(), e, true);
        }
    }

    @Retry(name = "mongoRetry")
    @CircuitBreaker(name = "mongoBreaker")
    private List<LocalImage> findByCreatorId(String creatorId) {
        try {
            return localStorageImageRepository.findByCreatorId(creatorId);
        } catch (Exception e) {
            log.warn("MongoDB findByCreatorId failed for creatorId: {} - will retry", creatorId);
            throw new ImageStorageException("MongoDB query failed for creatorId: " + creatorId, e, true);
        }
    }

    @Retry(name = "mongoRetry")
    @CircuitBreaker(name = "mongoBreaker")
    private LocalImage findById(String imageId) {
        try {
            return localStorageImageRepository.findById(imageId)
                    .orElseThrow(() -> new ImageNotFoundException(imageId));
        } catch (ImageNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.warn("MongoDB findById failed for imageId: {} - will retry", imageId);
            throw new ImageStorageException("MongoDB query failed for imageId: " + imageId, e, true);
        }
    }
}
