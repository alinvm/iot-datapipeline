package org.example.iot.sender;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {

        int devices = Integer.parseInt(System.getenv().getOrDefault("DEVICES", "5"));
        String ingressUrl = System.getenv().getOrDefault("INGRESS_URL", "http://ingress:8082/ingress/readings");

        System.out.println("Starting device-sender with " + devices + " devices → " + ingressUrl);

        HttpClient client = HttpClient.newHttpClient();
        ScheduledExecutorService pool = Executors.newScheduledThreadPool(Math.min(devices, 32));

        List<UUID> deviceIds = new ArrayList<>();
        for (int i = 0; i < devices; i++) deviceIds.add(UUID.randomUUID());

        AtomicInteger counter = new AtomicInteger();

        for (UUID deviceId : deviceIds) {
            pool.scheduleAtFixedRate(() -> {
                try {
                    long ts = Instant.now().toEpochMilli();
                    double value = 18 + Math.random() * 8;

                    Map<String, Object> body = Map.of(
                            "deviceId", deviceId.toString(),
                            "timestamp", ts,
                            "type", "temperature",
                            "unit", "C",
                            "value", value
                    );

                    String json = objectMapper.writeValueAsString(body);

                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(ingressUrl))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();

                    client.sendAsync(req, HttpResponse.BodyHandlers.discarding());

                    int c = counter.incrementAndGet();
                    if (c % 100 == 0) {
                        System.out.println("Sent " + c + " measurements…");
                        System.out.println("Sent " + body.toString());
                    }

                } catch (Exception e) {
                    System.err.println("Send error: " + e);
                }

            }, 0, 1, TimeUnit.SECONDS);
        }
    }
}
