package com.greengrub.image_service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Requires live MongoDB and Eureka — skipped in CI")
@SpringBootTest
class ImageServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
