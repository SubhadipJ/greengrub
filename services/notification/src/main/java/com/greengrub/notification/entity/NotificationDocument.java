package com.greengrub.notification.entity;

import com.greengrub.notification.enums.DonationStatus;
import com.greengrub.notification.enums.NotificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDocument {

    @Id
    private String id;

    private String donationId;

    private DonationStatus eventType;

    private NotificationStatus status;

    private String recipient;

    private String payload;

    private String failureReason;

    private LocalDateTime notificationTime;

    private LocalDateTime sentAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
