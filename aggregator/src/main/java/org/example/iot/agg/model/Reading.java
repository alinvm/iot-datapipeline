package org.example.iot.agg.model;

import java.util.UUID;

public record Reading(
        UUID deviceId,
        long timestamp,
        String type,     // e.g., "temperature"
        String unit,     // e.g., "C"
        double value
) {}
