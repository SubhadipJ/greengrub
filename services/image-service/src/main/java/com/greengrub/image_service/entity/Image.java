package com.greengrub.image_service.entity;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import com.greengrub.image_service.enumeration.CreatorType;
import lombok.*;
import org.springframework.context.annotation.Profile;

import java.time.LocalDateTime;

@Document(collectionName = "images")
@AllArgsConstructor
@NoArgsConstructor
@Data
@ToString
@Builder
@Profile("k8s")
public class Image {

    @DocumentId
    private String imageId;

    private String creatorId;

    private CreatorType creatorType;

    private String imageUrl;

    private String fileName;

    private String contentType;

    private Timestamp createdDate;
}
