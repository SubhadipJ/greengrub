package com.greengrub.notification.config;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class KafkaConfigTest {

    private KafkaConfig kafkaConfig;

    @BeforeEach
    void setUp() {
        kafkaConfig = new KafkaConfig();
        ReflectionTestUtils.setField(kafkaConfig, "bootstrapServers", "localhost:9092");
    }

    // ── dltProducerFactory ────────────────────────────────────────────────────

    @Test
    void dltProducerFactory_isNotNull() {
        assertThat(kafkaConfig.dltProducerFactory()).isNotNull();
    }

    @Test
    void dltProducerFactory_hasCorrectBootstrapServers() {
        var config = kafkaConfig.dltProducerFactory().getConfigurationProperties();
        assertThat(config.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("localhost:9092");
    }

    @Test
    void dltProducerFactory_usesStringKeySerializer() {
        var config = kafkaConfig.dltProducerFactory().getConfigurationProperties();
        assertThat(config.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)).isEqualTo(StringSerializer.class);
    }

    @Test
    void dltProducerFactory_usesJsonValueSerializer() {
        var config = kafkaConfig.dltProducerFactory().getConfigurationProperties();
        assertThat(config.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)).isEqualTo(JsonSerializer.class);
    }

    // ── dltKafkaTemplate ──────────────────────────────────────────────────────

    @Test
    void dltKafkaTemplate_isNotNull() {
        assertThat(kafkaConfig.dltKafkaTemplate()).isNotNull();
    }

    @Test
    void dltKafkaTemplate_isKafkaTemplateInstance() {
        assertThat(kafkaConfig.dltKafkaTemplate()).isInstanceOf(KafkaTemplate.class);
    }

    // ── kafkaErrorHandler ─────────────────────────────────────────────────────

    @Test
    void kafkaErrorHandler_isNotNull() {
        assertThat(kafkaConfig.kafkaErrorHandler(kafkaConfig.dltKafkaTemplate())).isNotNull();
    }

    @Test
    void kafkaErrorHandler_isDefaultErrorHandlerInstance() {
        CommonErrorHandler handler = kafkaConfig.kafkaErrorHandler(kafkaConfig.dltKafkaTemplate());
        assertThat(handler).isInstanceOf(DefaultErrorHandler.class);
    }

    @Test
    void kafkaErrorHandler_callNotPermittedExceptionRegisteredAsNotRetryable() {
        // removeClassification returns false = not-retryable, true = retryable, null = not registered.
        // CallNotPermittedException must be not-retryable so an open circuit breaker does not
        // burn through retry slots — the message goes straight to the DLT.
        DefaultErrorHandler handler = (DefaultErrorHandler) kafkaConfig.kafkaErrorHandler(kafkaConfig.dltKafkaTemplate());
        Boolean classification = handler.removeClassification(CallNotPermittedException.class);
        assertThat(classification)
                .as("CallNotPermittedException should be registered as not-retryable (false)")
                .isNotNull()
                .isFalse();
    }

    @Test
    void kafkaErrorHandler_differentBrokerUrl_reflectedInProducerFactory() {
        ReflectionTestUtils.setField(kafkaConfig, "bootstrapServers", "kafka.utility.svc.cluster.local:9092");
        var config = kafkaConfig.dltProducerFactory().getConfigurationProperties();
        assertThat(config.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG))
                .isEqualTo("kafka.utility.svc.cluster.local:9092");
    }
}
