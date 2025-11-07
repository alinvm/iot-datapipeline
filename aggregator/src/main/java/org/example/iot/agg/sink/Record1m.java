package org.example.iot.agg.sink;

import org.example.iot.agg.TDigestAccumulator;

import java.util.UUID;

public record Record1m(UUID deviceId, int bucketMonth, long windowStartMs, TDigestAccumulator acc1m) {}

