package com.greengrub.notification.dto;

public record Customer(
        String id,
        String firstname,
        String lastname,
        String email,
        String phone
) {}
