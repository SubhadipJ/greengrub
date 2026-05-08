package com.greengrub.image_service;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;

import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.util.ArrayList;

@Entity
@Table(name = "images")
@AllArgsConstructor
@NoArgsConstructor
@Data
@ToString
@Builder
public class Image {

    @Id
    @Column(name = "image_id", nullable = false, length = 36)
    private String imageId;

    @Column(name = "creator_id", nullable = false, length = 100)
    private String creatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "creator_type", nullable = false, length = 20)
    private CreatorType creatorType;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;
}
