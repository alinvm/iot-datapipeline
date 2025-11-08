package org.example.iot.ingress.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.iot.ingress.model.Reading;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReadingProducer {
    private final KafkaTemplate<String,String> kafka;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String topic = System.getenv().getOrDefault("KAFKA_TOPIC","iot.readings");

    public ReadingProducer(KafkaTemplate<String,String> kafka) { this.kafka = kafka; }

    public void send(Reading dto) throws Exception {
        // key by deviceId to maintain partition affinity
        kafka.send(topic, dto.deviceId().toString(), mapper.writeValueAsString(dto));
    }
}
