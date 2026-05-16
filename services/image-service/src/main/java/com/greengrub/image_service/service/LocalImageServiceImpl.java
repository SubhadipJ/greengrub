package com.greengrub.image_service.service;

import com.google.protobuf.ByteString;
import com.greengrub.image_service.entity.LocalImage;
import com.greengrub.image_service.enumeration.CreatorType;
import com.greengrub.image_service.helper.ImageHelper;
import com.greengrub.image_service.repository.LocalStorageImageRepository;
import com.greengrub.proto.image.*;
import io.grpc.Status;
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

    LocalStorageImageRepository localStorageImageRepository;

    @Override
    public void uploadImages(UploadImagesRequest request, StreamObserver<UploadImagesResponse> responseObserver) {
        String creatorId = request.getCreatorId();
        String creatorType = request.getCreatorType().toString();
        List<String> messages = new ArrayList<>();

        log.info("Received upload request for creatorId in Local: {} | {} images", creatorId, request.getImageDataCount());

        try {
            for (ByteString byteString : request.getImageDataList()) {
                byte[] imageBytes = byteString.toByteArray();
                String imageId = UUID.randomUUID().toString();

                if (imageBytes.length == 0) {
                    continue;
                }

                // Save metadata to MongoDB
                LocalImage entity = LocalImage.builder()
                    .imageId(imageId)
                    .creatorId(creatorId)
                    .creatorType(CreatorType.valueOf(creatorType))
                    .fileName(request.getFileName())
                    .contentType(request.getContentType())
                    .imageData(imageBytes)
                    .createdDate(LocalDateTime.now())
                    .build();

                localStorageImageRepository.save(entity);
                messages.add(request.getFileName() + " | got stored with id : " + imageId);
                log.info("Successfully uploaded image in Local: {} | details: {}", imageId, entity);
            }
            UploadImagesResponse response = UploadImagesResponse.newBuilder()
                    .addAllMessage(messages)
                    .setTotalImageCount(String.valueOf(messages.size()))
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error while uploading images for creatorId: {}", creatorId, e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to upload images: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getImagesByCreator(GetImagesByCreatorRequest request, StreamObserver<GetImagesByCreatorResponse> responseObserver){
        String creatorId = request.getCreatorId();
        try {
            log.info("Fetching images for creatorId: {}", creatorId);

            //fetch all the image related details for the creator
            List<LocalImage> entities = localStorageImageRepository.findByCreatorId(creatorId);

            List<com.greengrub.proto.image.Image> protoImages = entities.stream()
                    .map(ImageHelper::getImageFromLocalImage)
                    .map(ImageHelper::getProtoImageFromServiceImage)
                    .toList();

            GetImagesByCreatorResponse response = GetImagesByCreatorResponse.newBuilder()
                    .addAllImages(protoImages)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }  catch (Exception e) {
            log.error("Error while fetching images for creatorId: {}", creatorId, e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to fetch images: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void deleteImagesByImageId(ImageByImageIdRequest request, StreamObserver<DeleteImageByImageIdResponse> responseObserver) {
        try {
            if (request.getImageId().trim().isEmpty()) {
                throw new IllegalArgumentException("Image ID cannot be empty");
            }
            LocalImage localImage = localStorageImageRepository.findById(request.getImageId()).orElse(null);
            boolean deleted = localStorageImageRepository.deleteByImageId(request.getImageId());

            String responseMessage = (localImage != null) ?
                "Successfully deleted image in Local: " + localImage.getFileName():
                "Image deleted successfully";

            DeleteImageByImageIdResponse response = DeleteImageByImageIdResponse.newBuilder()
                    .setMessage(deleted ? responseMessage : "Image not found")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to delete image: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getImageByImageId(ImageByImageIdRequest request,
                                  StreamObserver<GetImagesByCreatorResponse> responseObserver) {
        String imageId = request.getImageId();
        try {
            if (request.getImageId().trim().isEmpty()) {
                throw new IllegalArgumentException("Image ID cannot be empty");
            }

            // Fetch single image
            LocalImage localImage = localStorageImageRepository.findById(imageId)
                    .orElseThrow(() -> new RuntimeException("Image not found with id: " + imageId));

            // Convert to Proto
            Image protoImage = ImageHelper.getProtoImageFromLocalImage(localImage);

            // Build response (even for single image)
            GetImagesByCreatorResponse response = GetImagesByCreatorResponse.newBuilder()
                    .addImages(protoImage)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (RuntimeException e) {   // Image not found
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Image not found with id: " + request.getImageId())
                    .asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to fetch image: " + e.getMessage())
                    .asRuntimeException());
        }
    }
}
