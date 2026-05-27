package com.greengrub.image_service;

import com.google.cloud.spring.autoconfigure.firestore.GcpFirestoreAutoConfiguration;
import com.google.cloud.spring.autoconfigure.storage.GcpStorageAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.cloud.gcp.firestore.enabled=false",
        "spring.cloud.gcp.storage.enabled=false",
        "spring.autoconfigure.exclude=" +
                "com.google.cloud.spring.autoconfigure.firestore.GcpFirestoreAutoConfiguration," +
                "com.google.cloud.spring.autoconfigure.storage.GcpStorageAutoConfiguration"
})
class ImageServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
