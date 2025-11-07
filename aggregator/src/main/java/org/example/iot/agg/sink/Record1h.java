package org.example.iot.agg.sink;

import org.example.iot.agg.TDigestAccumulator;

import java.util.UUID;

public record Record1h(UUID deviceId, int bucketMonth, long hourStartMs, TDigestAccumulator acc1h) {}

