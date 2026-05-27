package com.greengrub.image_service.config;

import com.google.cloud.spring.autoconfigure.core.GcpContextAutoConfiguration;
import com.google.cloud.spring.autoconfigure.firestore.GcpFirestoreAutoConfiguration;
import com.google.cloud.spring.autoconfigure.firestore.FirestoreTransactionManagerAutoConfiguration;
import com.google.cloud.spring.autoconfigure.storage.GcpStorageAutoConfiguration;
import com.google.cloud.spring.data.firestore.repository.config.EnableReactiveFirestoreRepositories;
import com.greengrub.image_service.repository.GCPStorageImageRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("k8s")
@Import({
        GcpContextAutoConfiguration.class,
        GcpFirestoreAutoConfiguration.class,
        FirestoreTransactionManagerAutoConfiguration.class,
        GcpStorageAutoConfiguration.class
})
@EnableReactiveFirestoreRepositories(basePackageClasses = GCPStorageImageRepository.class)
public class FirestoreRepositoryConfig {
}
