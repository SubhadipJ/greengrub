package com.greengrub.notification.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record Donation(
        String donationId,
        String donorName,
        String donorEmail,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        Customer customer,
        String organizationName,
        String status,
        List<DonatedItem> items
) {}
