package com.greengrub.image_service.entity;

import com.greengrub.image_service.enumeration.CreatorType;
import lombok.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "images")
@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
@ToString
@Profile("local")
public class LocalImage {

    @Id
    private String imageId;

    private String creatorId;

    private CreatorType creatorType;

    private String fileName;

    private String contentType;

    private byte[] imageData;

    private LocalDateTime createdDate;
}
