package com.greengrub.notification;

import com.greengrub.notification.repository.NotificationMongoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.thymeleaf.spring6.SpringTemplateEngine;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=" +
                "com.google.cloud.spring.autoconfigure.firestore.GcpFirestoreAutoConfiguration," +
                "com.google.cloud.spring.autoconfigure.firestore.FirestoreRepositoriesAutoConfiguration," +
                "com.google.cloud.spring.autoconfigure.firestore.FirestoreTransactionManagerAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration," +
                "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration," +
                "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration," +
                "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration," +
                "org.springframework.cloud.config.client.ConfigClientAutoConfiguration",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.mail.host=localhost",
        "spring.cloud.config.enabled=false",
        "spring.config.import=optional:configserver:"
})
class NotificationApplicationTests {

    @MockBean
    private NotificationMongoRepository notificationMongoRepository;

    @MockBean
    private JavaMailSender javaMailSender;

    @MockBean
    private SpringTemplateEngine springTemplateEngine;

    @Test
    void contextLoads() {
    }

}
