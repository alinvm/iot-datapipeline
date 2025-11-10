package org.example.iot.ingress.api;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@Testcontainers
@Disabled
class IngressControllerTestIT {

    @Container
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap", () -> kafka.getBootstrapServers());
        // add any serializer/deserializer config you need
        r.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ConsumerFactory<String, String> cf;

    @Test
    void postReading_producesKafkaMessage() throws Exception {
        var json = """
      {"deviceId":"11111111-1111-1111-1111-111111111111","timestamp":1700000000000,"type":"temperature","unit":"C","value":25.0}
    """;
        mvc.perform(post("/ingress/readings").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isAccepted());

        try (var consumer = cf.createConsumer()) {
            consumer.subscribe(List.of("iot.readings"));
            var recs = consumer.poll(Duration.ofSeconds(5));
            assertFalse(recs.isEmpty(), "expected one message");
            var value = recs.iterator().next().value();
            assertTrue(value.contains("\"timestamp\":1700000000000"));
        }
    }
}