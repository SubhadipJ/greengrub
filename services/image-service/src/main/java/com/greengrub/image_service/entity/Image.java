package com.greengrub.image_service.entity;

import com.greengrub.image_service.enumeration.CreatorType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Entity
@Document(collection = "images")
@AllArgsConstructor
@NoArgsConstructor
@Data
@ToString
@Builder
@Profile("k8s")
public class Image {

    @Id
    @Column(name = "image_id", nullable = false, length = 36)
    private String imageId;

    @Column(name = "creator_id", nullable = false, length = 100)
    private String creatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "creator_type", nullable = false, length = 20)
    private CreatorType creatorType;

    @Column(name = "image_url", nullable = true, length = 1000)
    private String imageUrl;

    @Column(name = "file_name", nullable = false, length = 100)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;
}
