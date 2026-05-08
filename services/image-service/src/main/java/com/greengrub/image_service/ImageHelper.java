package com.greengrub.image_service;

import java.time.LocalDateTime;

public class ImageHelper {

    public static Image getServiceImageFromProtoImage(com.greengrub.proto.image.Image image) {
        return Image.builder()
                .imageUrl(image.getImageUrl())
                .creatorId(image.getCreatorId())
                .createdDate(LocalDateTime.parse(image.getCreatedDate()))
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
}
