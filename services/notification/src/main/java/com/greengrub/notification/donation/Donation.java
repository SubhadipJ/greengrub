package com.greengrub.notification.donation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


public record Donation(
         String donationId,
         String donorName,
         String donorEmail,
         BigDecimal totalAmount,
         LocalDateTime createdAt,
         Customer campaignName,
         String organizationName,
         List<DonatedItem> items
) {
}
