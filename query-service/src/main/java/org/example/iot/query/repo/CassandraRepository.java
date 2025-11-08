package org.example.iot.query.repo;


import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CassandraRepository {

    private final CqlSession session;

    private final PreparedStatement select1m;
    private final PreparedStatement select1h;
    private final PreparedStatement selectAll;

    public CassandraRepository(CqlSession session) {
        this.session = session;

        this.select1m = session.prepare(
                "SELECT bucket_month, window_start, count, sum, min, max, tdigest " +
                        "FROM agg_device_1m WHERE device_id = ? AND bucket_month = ? " +
                        "ORDER BY window_start DESC LIMIT 1");

        this.select1h = session.prepare(
                "SELECT bucket_month, hour_start, count, sum, min, max, tdigest " +
                        "FROM agg_device_1h WHERE device_id = ? AND bucket_month = ? " +
                        "ORDER BY hour_start DESC LIMIT 1");

        this.selectAll = session.prepare(
                "SELECT count, sum, min, max, tdigest FROM agg_device_alltime WHERE device_id = ?");
    }

    public Optional<Row> latest1m(UUID deviceId, int bucketMonth) {
        return Optional.ofNullable(session.execute(select1m.bind(deviceId, bucketMonth)).one());
    }

    public Optional<Row> latest1h(UUID deviceId, int bucketMonth) {
        return Optional.ofNullable(session.execute(select1h.bind(deviceId, bucketMonth)).one());
    }

    public Optional<Row> allTime(UUID deviceId) {
        return Optional.ofNullable(session.execute(selectAll.bind(deviceId)).one());
    }
}
