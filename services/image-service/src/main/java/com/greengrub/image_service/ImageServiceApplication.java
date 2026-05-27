package com.greengrub.image_service;

import com.google.cloud.spring.autoconfigure.core.GcpContextAutoConfiguration;
import com.google.cloud.spring.autoconfigure.firestore.FirestoreRepositoriesAutoConfiguration;
import com.google.cloud.spring.autoconfigure.firestore.FirestoreTransactionManagerAutoConfiguration;
import com.google.cloud.spring.autoconfigure.firestore.GcpFirestoreAutoConfiguration;
import com.google.cloud.spring.autoconfigure.storage.GcpStorageAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
        GcpContextAutoConfiguration.class,
        GcpFirestoreAutoConfiguration.class,
        FirestoreRepositoriesAutoConfiguration.class,
        FirestoreTransactionManagerAutoConfiguration.class,
        GcpStorageAutoConfiguration.class
})
public class ImageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageServiceApplication.class, args);
    }

}
