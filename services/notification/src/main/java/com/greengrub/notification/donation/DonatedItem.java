package com.greengrub.notification.donation;

public record DonatedItem(
         String foodName,
         Integer quantity,
         String unit   // e.g. kg, pieces, packets
) {
}