package com.greengrub.image_service.enumeration;

public enum CreatorType {
    CUSTOMER,
    FOOD_REQUEST;

    public static CreatorType fromString(String value) {
        return CreatorType.valueOf(value);
    }
}
