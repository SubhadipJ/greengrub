package com.greengrub.notification.donation;

public record Customer(
        String Id,
        String firstname,
        String lastname,
        String email,
        String phone
) {
}
