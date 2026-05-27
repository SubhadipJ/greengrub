package com.greengrub.image_service.mapper;

import com.google.cloud.Timestamp;
import com.greengrub.image_service.entity.Image;
import com.greengrub.image_service.entity.LocalImage;
import com.greengrub.image_service.enumeration.CreatorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class ImageMapperTest {

    private Image serviceImage;
    private LocalImage localImage;
    private com.greengrub.proto.image.Image protoImage;

    @BeforeEach
    void setUp() {
        // Use a timestamp whose toString() yields a plain ISO local datetime without 'Z'
        // so LocalDateTime.parse() in getLocalImageFromImageWithImageBytes can parse it.
        // Timestamp.ofTimeSecondsAndNanos produces "2024-01-15T10:30:00" when nanos=0
        // but the actual format is RFC3339 with trailing 'Z'. We therefore build the
        // Timestamp from a string that Timestamp itself accepts and that has no fractional
        // seconds, and we use a fixed value whose toString we can control for the
        // getLocalImage* tests by using a LocalImage directly.
        Timestamp ts = Timestamp.parseTimestamp("2024-01-15T10:30:00Z");

        serviceImage = Image.builder()
                .imageId("img-001")
                .creatorId("creator-001")
                .creatorType(CreatorType.CUSTOMER)
                .imageUrl("https://storage.googleapis.com/bucket/img-001")
                .fileName("photo.jpg")
                .contentType("image/jpeg")
                .createdDate(ts)
                .build();

        localImage = LocalImage.builder()
                .imageId("local-001")
                .creatorId("creator-001")
                .creatorType(CreatorType.FOOD_REQUEST)
                .fileName("food.jpg")
                .contentType("image/jpeg")
                .imageData(new byte[]{1, 2, 3})
                .createdDate(LocalDateTime.of(2024, 1, 15, 10, 30, 0))
                .build();

        protoImage = com.greengrub.proto.image.Image.newBuilder()
                .setImageId("proto-001")
                .setImageUrl("https://storage.googleapis.com/bucket/proto-001")
                .setCreatorId("creator-001")
                .setCreatorType(com.greengrub.proto.image.CreatorType.CUSTOMER)
                .setCreatedDate(ts.toString())
                .build();
    }

    // ── getProtoImageFromServiceImage ────────────────────────────────────────

    @Test
    void getProtoImageFromServiceImage_mapsAllFields() {
        com.greengrub.proto.image.Image proto = ImageMapper.getProtoImageFromServiceImage(serviceImage);

        assertThat(proto.getImageId()).isEqualTo("img-001");
        assertThat(proto.getImageUrl()).isEqualTo("https://storage.googleapis.com/bucket/img-001");
        assertThat(proto.getCreatorId()).isEqualTo("creator-001");
        assertThat(proto.getCreatorType()).isEqualTo(com.greengrub.proto.image.CreatorType.CUSTOMER);
    }

    @Test
    void getProtoImageFromServiceImage_foodRequest_mapsCreatorType() {
        serviceImage.setCreatorType(CreatorType.FOOD_REQUEST);
        com.greengrub.proto.image.Image proto = ImageMapper.getProtoImageFromServiceImage(serviceImage);

        assertThat(proto.getCreatorType()).isEqualTo(com.greengrub.proto.image.CreatorType.FOOD_REQUEST);
    }

    // ── getServiceImageFromProtoImage ────────────────────────────────────────

    @Test
    void getServiceImageFromProtoImage_mapsAllFields() {
        Image result = ImageMapper.getServiceImageFromProtoImage(protoImage);

        assertThat(result.getImageId()).isEqualTo("proto-001");
        assertThat(result.getImageUrl()).isEqualTo("https://storage.googleapis.com/bucket/proto-001");
        assertThat(result.getCreatorId()).isEqualTo("creator-001");
        assertThat(result.getCreatorType()).isEqualTo(CreatorType.CUSTOMER);
    }

    // ── getImageFromLocalImage ───────────────────────────────────────────────

    @Test
    void getImageFromLocalImage_setsEmptyImageUrl() {
        Image result = ImageMapper.getImageFromLocalImage(localImage);

        assertThat(result.getImageUrl()).isEmpty();
        assertThat(result.getCreatorId()).isEqualTo("creator-001");
        assertThat(result.getCreatorType()).isEqualTo(CreatorType.FOOD_REQUEST);
        assertThat(result.getFileName()).isEqualTo("food.jpg");
        assertThat(result.getImageId()).isNotBlank(); // new UUID generated
    }

    @Test
    void getImageFromLocalImageWithImageUrl_setsProvidedUrl() {
        Image result = ImageMapper.getImageFromLocalImageWithImageUrl(localImage, "https://cdn.example.com/food.jpg");

        assertThat(result.getImageUrl()).isEqualTo("https://cdn.example.com/food.jpg");
        assertThat(result.getCreatorId()).isEqualTo("creator-001");
    }

    @Test
    void getImageFromLocalImage_generatesFreshUuid() {
        Image result1 = ImageMapper.getImageFromLocalImage(localImage);
        Image result2 = ImageMapper.getImageFromLocalImage(localImage);

        assertThat(result1.getImageId()).isNotEqualTo(result2.getImageId());
    }

    // ── getLocalImageFromImage ───────────────────────────────────────────────

    @Test
    void getLocalImageFromImage_setsNullImageData() {
        LocalImage result = ImageMapper.getLocalImageFromImage(serviceImage);

        assertThat(result.getImageData()).isNull();
        assertThat(result.getCreatorId()).isEqualTo("creator-001");
        assertThat(result.getCreatorType()).isEqualTo(CreatorType.CUSTOMER);
        assertThat(result.getFileName()).isEqualTo("photo.jpg");
        assertThat(result.getImageId()).isNotBlank();
    }

    @Test
    void getLocalImageFromImageWithImageBytes_setsProvidedBytes() {
        byte[] bytes = {10, 20, 30};
        LocalImage result = ImageMapper.getLocalImageFromImageWithImageBytes(serviceImage, bytes);

        assertThat(result.getImageData()).isEqualTo(bytes);
        assertThat(result.getCreatorId()).isEqualTo("creator-001");
    }

    // ── getProtoImageFromLocalImage ──────────────────────────────────────────

    @Test
    void getProtoImageFromLocalImage_setsEmptyImageUrl() {
        com.greengrub.proto.image.Image proto = ImageMapper.getProtoImageFromLocalImage(localImage);

        assertThat(proto.getImageUrl()).isEmpty();
        assertThat(proto.getImageId()).isEqualTo("local-001");
        assertThat(proto.getCreatorId()).isEqualTo("creator-001");
        assertThat(proto.getCreatorType()).isEqualTo(com.greengrub.proto.image.CreatorType.FOOD_REQUEST);
    }

    @Test
    void getProtoImageFromLocalImage_customer_mapsCreatorType() {
        localImage.setCreatorType(CreatorType.CUSTOMER);
        com.greengrub.proto.image.Image proto = ImageMapper.getProtoImageFromLocalImage(localImage);

        assertThat(proto.getCreatorType()).isEqualTo(com.greengrub.proto.image.CreatorType.CUSTOMER);
    }
}
