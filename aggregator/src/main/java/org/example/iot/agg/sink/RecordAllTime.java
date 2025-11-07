package org.example.iot.agg.sink;

import org.example.iot.agg.TDigestAccumulator;

import java.util.UUID;

public record RecordAllTime(UUID deviceId, TDigestAccumulator accAllTime) {}
