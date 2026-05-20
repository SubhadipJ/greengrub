package com.greengrub.notification.dto;

public record DonatedItem(
        String foodName,
        Integer quantity,
        String unit,
        String category
) {}
