package org.example.iot.query.service;


import com.tdunning.math.stats.MergingDigest;
import com.tdunning.math.stats.TDigest;
import org.example.iot.query.repo.CassandraRepository;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static java.time.ZoneOffset.UTC;

@Service
public class QueryService {

    private final CassandraRepository repo;

    public QueryService(CassandraRepository repo) {
        this.repo = repo;
    }

    private int bucketMonth(long timestamp) {
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), UTC);
        return zonedDateTime.getYear() * 100 + zonedDateTime.getMonthValue(); // e.g., 202511
    }

    private double median(ByteBuffer digestBytes) {
        ByteBuffer copy = digestBytes.duplicate(); // avoid modifying original buffer
        MergingDigest d = MergingDigest.fromBytes(copy);
        return d.quantile(0.5);
    }

    public Map<String, String> latest1m(UUID deviceId) {
        int bucket = bucketMonth(System.currentTimeMillis());
        return repo.latest1m(deviceId, bucket)
                .map(row -> Map.of(
                        "avg", String.valueOf(truncateDoubleTo2Decimals(row.getDouble("sum") / row.getLong("count"))),
                        "min", String.valueOf(row.getDouble("min")),
                        "max", String.valueOf(row.getDouble("max")),
                        "median", String.valueOf(truncateDoubleTo2Decimals(median(row.getByteBuffer("tdigest"))))
                )).orElse(Collections.emptyMap());
    }

    public Map<String, String> latest1h(UUID deviceId) {
        int bucket = bucketMonth(System.currentTimeMillis());
        return repo.latest1h(deviceId, bucket)
                .map(row -> Map.of(
                        "avg", String.valueOf(truncateDoubleTo2Decimals(row.getDouble("sum") / row.getLong("count"))),
                        "min", String.valueOf(row.getDouble("min")),
                        "max", String.valueOf(row.getDouble("max")),
                        "median", String.valueOf(truncateDoubleTo2Decimals(median(row.getByteBuffer("tdigest"))))
                )).orElse(Collections.emptyMap());
    }

    public Map<String, String> allTime(UUID deviceId) {
        return repo.allTime(deviceId)
                .map(row -> Map.of(
                        "avg", String.valueOf(truncateDoubleTo2Decimals(row.getDouble("sum") / row.getLong("count"))),
                        "min", String.valueOf(row.getDouble("min")),
                        "max", String.valueOf(row.getDouble("max")),
                        "median", String.valueOf(truncateDoubleTo2Decimals(median(row.getByteBuffer("tdigest"))))
                )).orElse(Collections.emptyMap());
    }

    private double truncateDoubleTo2Decimals(double value) {
        return Math.floor(value * 100) / 100;
    }
}
