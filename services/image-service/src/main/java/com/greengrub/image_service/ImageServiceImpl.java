package com.greengrub.image_service;


import com.google.protobuf.ByteString;
import com.greengrub.proto.foods.FoodServiceGrpc;
import com.greengrub.proto.image.*;
import io.grpc.stub.StreamObserver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@GrpcService
@AllArgsConstructor
public class ImageServiceImpl extends ImageServiceGrpc.ImageServiceImplBase {

    private static ImageRepository imageRepository;
    private static GcpStorageService gcpStorageService;

    @Override
    public void uploadImages(UploadImagesRequest request, StreamObserver<UploadImagesResponse> responseObserver) {

        String creatorId = request.getCreatorId();
        String creatorType = request.getCreatorType().toString();

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
                com.greengrub.image_service.Image entity = com.greengrub.image_service.Image.builder()
                        .imageId(imageId)
                        .creatorId(creatorId)
                        .creatorType(CreatorType.valueOf(creatorType))
                        .imageUrl(imageUrl)
                        .createdDate(LocalDateTime.now())
                        .build();

                imageRepository.save(entity);

                log.info("Successfully uploaded image: {} | URL: {}", imageId, imageUrl);
            }

        UploadImagesResponse response = UploadImagesResponse.newBuilder()
                .setMessage("Successfully uploaded image(s)")
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

        log.info("Fetching images for creatorId: {}", creatorId);

        //fetch all the image related details for the creator
        List<com.greengrub.image_service.Image> entities = imageRepository.findByCreatorId(creatorId);

        List<com.greengrub.proto.image.Image> protoImages = entities.stream()
                .map(ImageHelper::getProtoImageFromServiceImage)
                .toList();

        GetImagesByCreatorResponse response = GetImagesByCreatorResponse.newBuilder()
                .addAllImages(protoImages)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

}
