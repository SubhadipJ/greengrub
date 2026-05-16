package com.greengrub.image_service.service;


import com.google.protobuf.ByteString;
import com.greengrub.image_service.enumeration.CreatorType;
import com.greengrub.image_service.entity.Image;
import com.greengrub.image_service.helper.ImageHelper;
import com.greengrub.image_service.repository.GCPStorageImageRepository;
import com.greengrub.proto.image.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.context.annotation.Profile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@GrpcService
@AllArgsConstructor
@Profile("k8s")
public class ImageServiceImpl extends ImageServiceGrpc.ImageServiceImplBase {

    private static GCPStorageImageRepository GCPStorageImageRepository;
    private static GcpStorageService gcpStorageService;

    @Override
    public void uploadImages(UploadImagesRequest request, StreamObserver<UploadImagesResponse> responseObserver) {

        String creatorId = request.getCreatorId();
        String creatorType = request.getCreatorType().toString();
        List<String> messages = new ArrayList<>();
        log.info("Received upload request for creatorId: {} | {} images", creatorId, request.getImageDataCount());
        try {
            for (ByteString byteString : request.getImageDataList()) {
                byte[] imageBytes = byteString.toByteArray();
                String imageId = UUID.randomUUID().toString();

                if (imageBytes.length == 0) {
                    continue;
                }

                // Upload to GCP Cloud Storage
                String imageUrl = "This is test URL";
//                        gcpStorageService.uploadImage(imageBytes, imageId, creatorId);

                // Save metadata to MongoDB
                Image entity = Image.builder()
                        .imageId(imageId)
                        .creatorId(creatorId)
                        .creatorType(CreatorType.valueOf(creatorType))
                        .fileName(request.getFileName())
                        .contentType(request.getContentType())
                        .imageUrl(imageUrl)
                        .createdDate(LocalDateTime.now())
                        .build();

                GCPStorageImageRepository.save(entity);
                messages.add(request.getFileName() + " | got stored with id : " + imageId);

                log.info("Successfully uploaded image: {} | URL: {}", imageId, imageUrl);
            }

        UploadImagesResponse response = UploadImagesResponse.newBuilder()
                .addAllMessage(messages)
                .setTotalImageCount(String.valueOf(messages.size()))
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();

    } catch (Exception e) {
        log.error("Error while uploading images for creatorId: {}", creatorId, e);
        responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("Failed to upload images: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void getImagesByCreator(GetImagesByCreatorRequest request, StreamObserver<GetImagesByCreatorResponse> responseObserver) {
        String creatorId = request.getCreatorId();
        try {
            log.info("Fetching images for creatorId: {}", creatorId);

            //fetch all the image related details for the creator
            List<Image> entities = GCPStorageImageRepository.findByCreatorId(creatorId);

            List<com.greengrub.proto.image.Image> protoImages = entities.stream()
                    .map(ImageHelper::getProtoImageFromServiceImage)
                    .toList();

            GetImagesByCreatorResponse response = GetImagesByCreatorResponse.newBuilder()
                    .addAllImages(protoImages)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error while fetching images for creatorId: {}", creatorId, e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to fetch images: " + e.getMessage())
                    .asRuntimeException());
        }
        //    List<ImageDocument> imageDocuments = imageService.getImagesByCreator(
//            request.getCreatorId(),
//            request.getLimit() > 0 ? request.getLimit() : 50); // fetch data in chunks
//            // Option 1: Send ALL images in ONE response (Simple)
//            if (true) {   // Change condition as needed
//                GetImagesByCreatorResponse response = GetImagesByCreatorResponse.newBuilder()
//                        .addAllImages(mapToProtoList(imageDocuments))   // Important
//                        .build();
//
//                responseObserver.onNext(response);
//                responseObserver.onCompleted();
//                return;
//            }
//
//            // Option 2: Send images in batches / one by one (Better for large data)
//            for (ImageDocument doc : imageDocuments) {
//
//                Image protoImage = mapToProtoImage(doc);   // Convert entity to proto
//
//                GetImagesByCreatorResponse response = GetImagesByCreatorResponse.newBuilder()
//                        .addImages(protoImage)                    // Add one image at a time
//                        .build();
//
//                responseObserver.onNext(response);            // Send one response per image
//            }
//
//            responseObserver.onCompleted();
//        }
    }

    public void deleteImagesByImageId(ImageByImageIdRequest request, StreamObserver<DeleteImageByImageIdResponse> responseObserver) {
        try {
            if (request.getImageId().trim().isEmpty()) {
                throw new IllegalArgumentException("Image ID cannot be empty");
            }
            Image image = GCPStorageImageRepository.findById(request.getImageId()).orElse(null);
            boolean deleted = GCPStorageImageRepository.deleteByImageId(request.getImageId());

            String responseMessage = (image != null) ?
                    "Successfully deleted image in Local: " + image.getFileName():
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
            Image image = GCPStorageImageRepository.findById(imageId)
                    .orElseThrow(() -> new RuntimeException("Image not found with id: " + imageId));

            // Convert to Proto
            com.greengrub.proto.image.Image protoImage = ImageHelper.getProtoImageFromServiceImage(image);

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
