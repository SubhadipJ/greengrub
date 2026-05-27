package com.greengrub.image_service.enumeration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CreatorTypeTest {

    @Test
    void fromString_customer_returnsCustomer() {
        assertThat(CreatorType.fromString("CUSTOMER")).isEqualTo(CreatorType.CUSTOMER);
    }

    @Test
    void fromString_foodRequest_returnsFoodRequest() {
        assertThat(CreatorType.fromString("FOOD_REQUEST")).isEqualTo(CreatorType.FOOD_REQUEST);
    }

    @Test
    void fromString_invalid_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> CreatorType.fromString("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void values_containsExactlyTwoValues() {
        assertThat(CreatorType.values()).containsExactlyInAnyOrder(
                CreatorType.CUSTOMER, CreatorType.FOOD_REQUEST);
    }

    @Test
    void valueOf_customer_returnsCustomer() {
        assertThat(CreatorType.valueOf("CUSTOMER")).isEqualTo(CreatorType.CUSTOMER);
    }
}
