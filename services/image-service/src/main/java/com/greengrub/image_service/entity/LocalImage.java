package com.greengrub.image_service.entity;

import com.greengrub.image_service.enumeration.CreatorType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
@ToString
@Document(collection = "images")
@Profile("local")
public class LocalImage {
    @Id
    @Column(name = "image_id", nullable = false, length = 36)
    private String imageId;

    @Column(name = "creator_id", nullable = false, length = 100)
    private String creatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "creator_type", nullable = false, length = 20)
    private CreatorType creatorType;

    @Column(name = "file_name", nullable = false, length = 100)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Lob
    @Column(name = "image_data", columnDefinition = "BYTEA",  nullable = true)
    private byte[] imageData;

    private LocalDateTime createdDate;
}
