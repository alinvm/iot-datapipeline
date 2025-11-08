package org.example.iot.ingress.api;

import org.example.iot.ingress.kafka.ReadingProducer;
import org.example.iot.ingress.model.Reading;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ingress")
public class IngressController {
    private final ReadingProducer producer;
    public IngressController(ReadingProducer producer) { this.producer = producer; }

    @PostMapping("/readings")
    public ResponseEntity<?> ingest(@Validated @RequestBody Reading dto) throws Exception {
        producer.send(dto);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/health")
    public String health() { return "OK"; }
}
