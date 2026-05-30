package com.greengrub.image_service.mapper;

import com.google.cloud.Timestamp;
import com.google.protobuf.ByteString;
import com.greengrub.image_service.entity.LocalImage;
import com.greengrub.image_service.enumeration.CreatorType;
import com.greengrub.image_service.entity.Image;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ImageMapper {

    public static Image getServiceImageFromProtoImage(com.greengrub.proto.image.Image image) {
        return Image.builder()
                .imageUrl(image.getImageUrl())
                .creatorId(image.getCreatorId())
                .createdDate(Timestamp.parseTimestamp(image.getCreatedDate()))
                .creatorType(CreatorType.valueOf(image.getCreatorType().toString()))
                .imageId(image.getImageId())
                .build();
    }

    public static com.greengrub.proto.image.Image getProtoImageFromServiceImage(Image image) {
        return com.greengrub.proto.image.Image.newBuilder()
                .setImageId(image.getImageId())
                .setImageUrl(image.getImageUrl())
                .setCreatorId(image.getCreatorId())
                .setCreatorType(com.greengrub.proto.image.CreatorType.valueOf(image.getCreatorType().name()))
                .setCreatedDate(image.getCreatedDate().toString())
                .build();
    }

    public static Image getImageFromLocalImage(LocalImage localImage) {
        return getImageFromLocalImageWithImageUrl(localImage, "");
    }

    public static Image getImageFromLocalImageWithImageUrl(LocalImage localImage, String url) {
        return Image.builder()
                .imageId(UUID.randomUUID().toString())
                .creatorId(localImage.getCreatorId())
                .createdDate(Timestamp.parseTimestamp(localImage.getCreatedDate().toString()))
                .creatorType(CreatorType.valueOf(localImage.getCreatorType().toString()))
                .fileName(localImage.getFileName())
                .imageUrl(url)
                .build();
    }

    public static LocalImage getLocalImageFromImage(Image image) {
        return getLocalImageFromImageWithImageBytes(image, null);
    }

    public static LocalImage getLocalImageFromImageWithImageBytes(Image image, byte[] imageBytes) {
        return LocalImage.builder()
                .imageId(UUID.randomUUID().toString())
                .creatorId(image.getCreatorId())
                .createdDate(OffsetDateTime.parse(image.getCreatedDate().toString()).toLocalDateTime())
                .creatorType(CreatorType.valueOf(image.getCreatorType().toString()))
                .fileName(image.getFileName())
                .imageData(imageBytes)
                .build();
    }

    public static com.greengrub.proto.image.Image getProtoImageFromLocalImage(LocalImage image) {
        com.greengrub.proto.image.Image.Builder builder = com.greengrub.proto.image.Image.newBuilder()
                .setImageId(image.getImageId())
                .setImageUrl("")
                .setCreatorId(image.getCreatorId())
                .setCreatorType(com.greengrub.proto.image.CreatorType.valueOf(image.getCreatorType().name()))
                .setCreatedDate(image.getCreatedDate().toString())
                .setFileName(image.getFileName() != null ? image.getFileName() : "")
                .setContentType(image.getContentType() != null ? image.getContentType() : "");
        if (image.getImageData() != null) {
            builder.setImageData(ByteString.copyFrom(image.getImageData()));
        }
        return builder.build();
    }
}
