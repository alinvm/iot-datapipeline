package org.example.iot.agg;

import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.connectors.cassandra.CassandraSink;
import org.apache.flink.util.Collector;
import org.example.iot.agg.model.Reading;
import org.example.iot.agg.deserializer.ReadingDeserializationSchema;
import org.example.iot.agg.sink.Record1h;
import org.example.iot.agg.sink.Record1m;
import org.example.iot.agg.sink.RecordAllTime;
import org.example.iot.agg.util.TimeUtil;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.tuple.*;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;

public class IotAggJob {

    public static void main(String[] args) throws Exception {
        String kafkaBootstrap = arg(args, "--kafka.bootstrap", "kafka:9092");
        String kafkaTopic     = arg(args, "--kafka.topic", "iot.readings");
        String cassHost       = arg(args, "--cassandra.host", "cassandra");
        int cassPort          = Integer.parseInt(arg(args, "--cassandra.port", "9042"));
        String keyspace       = arg(args, "--cassandra.keyspace", "iot");
        long wmLatenessSec    = Long.parseLong(arg(args, "--wm.lateness.sec", "120"));

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(30_000);
        env.getConfig().setAutoWatermarkInterval(1000L);

        var source = KafkaSource.<Reading>builder()
                .setBootstrapServers(kafkaBootstrap)
                .setTopics(kafkaTopic)
                .setGroupId("flink-agg")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new ReadingDeserializationSchema())
                .build();

        var wm = WatermarkStrategy
                .<Reading>forBoundedOutOfOrderness(Duration.ofSeconds(wmLatenessSec))
                .withTimestampAssigner((r, ts) -> r.timestamp())
                .withIdleness(Duration.ofSeconds(10));

        var readings = env.fromSource(source, wm, "kafka-readings");

        var minuteAggs =
                readings
                    .keyBy(Reading::deviceId)
                    .window(TumblingEventTimeWindows.of(Duration.ofMinutes(1)))
                    .aggregate(new TDigestAggregateFunction(), new Processor1MinuteWindow());

        var hourAggs =
                readings
                    .keyBy(Reading::deviceId)
                    .window(TumblingEventTimeWindows.of(Duration.ofHours(1)))
                    .aggregate(new TDigestAggregateFunction(), new Processor1HourWindow());

        // All-time stream
        var allTimeAggs =
                minuteAggs
                    .keyBy(Record1m::deviceId)
                    .process(new ProcessorAllTime());

        // Map to Tuples for CassandraSink (binds ? by position)
        var oneMinuteTuples =
                minuteAggs.map(r -> Tuple8.of(
                        r.deviceId(),
                        r.bucketMonth(),
                        new Timestamp(r.windowStartMs()),
                        r.acc1m().getCount(),
                        r.acc1m().getSum(),
                        r.acc1m().getMin(),
                        r.acc1m().getMax(),
                        r.acc1m().toBytes()
                ))
                .returns(new TypeHint<Tuple8<UUID, Integer, Timestamp, Long, Double, Double, Double, ByteBuffer>>() {});

        var oneHourTuples =
                hourAggs.map(r -> Tuple8.of(
                        r.deviceId(),
                        r.bucketMonth(),
                        new Timestamp(r.hourStartMs()),
                        r.acc1h().getCount(),
                        r.acc1h().getSum(),
                        r.acc1h().getMin(),
                        r.acc1h().getMax(),
                        r.acc1h().toBytes()
                ))
                .returns(new TypeHint<Tuple8<UUID, Integer, java.sql.Timestamp, Long, Double, Double, Double, ByteBuffer>>() {});

        var allTimeTuples =
                allTimeAggs.map(r -> Tuple6.of(
                        r.deviceId(),
                        r.accAllTime().getCount(),
                        r.accAllTime().getSum(),
                        r.accAllTime().getMin(),
                        r.accAllTime().getMax(),
                        r.accAllTime().toBytes()
                ))
                .returns(new TypeHint<Tuple6<UUID, Long, Double, Double, Double, ByteBuffer>>() {});

        // ---- Cassandra sinks (official connector)
        CassandraSink.addSink(oneMinuteTuples)
                .setQuery("INSERT INTO " + keyspace + ".agg_device_1m " +
                        "(device_id, bucket_month, window_start, count, sum, min, max, tdigest) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .setHost(cassHost, cassPort)
                .build();

        CassandraSink.addSink(oneHourTuples)
                .setQuery("INSERT INTO " + keyspace + ".agg_device_1h " +
                        "(device_id, bucket_month, hour_start, count, sum, min, max, tdigest) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .setHost(cassHost, cassPort)
                .build();

        CassandraSink.addSink(allTimeTuples)
                .setQuery("INSERT INTO " + keyspace + ".agg_device_alltime " +
                        "(device_id, count, sum, min, max, tdigest) VALUES (?, ?, ?, ?, ?, ?)")
                .setHost(cassHost, cassPort)
                .build();

        env.execute("iot-aggregates-1m-1h-alltime");
    }

    private static String arg(String[] args, String key, String def) {
        for (int i = 0; i < args.length - 1; i++) if (key.equals(args[i])) return args[i + 1];
        return def;
    }

    static final class Processor1MinuteWindow extends ProcessWindowFunction<TDigestAccumulator, Record1m, UUID, TimeWindow> {
        @Override
        public void process(UUID key, Context ctx, Iterable<TDigestAccumulator> elements, Collector<Record1m> out) {
            TDigestAccumulator acc = elements.iterator().next();
            long start = TimeUtil.floorToMinuteStartMs(ctx.window().getStart());
            int bucket = TimeUtil.bucketMonth(start);
            out.collect(new Record1m(key, bucket, start, acc));
        }
    }

    static final class Processor1HourWindow extends ProcessWindowFunction<TDigestAccumulator, Record1h, UUID, TimeWindow> {
        @Override
        public void process(UUID key, Context ctx, Iterable<TDigestAccumulator> elements, Collector<Record1h> out) {
            TDigestAccumulator acc = elements.iterator().next();
            long start = TimeUtil.floorToHourStartMs(ctx.window().getStart());
            int bucket = TimeUtil.bucketMonth(start);
            out.collect(new Record1h(key, bucket, start, acc));
        }
    }

    static final class ProcessorAllTime extends KeyedProcessFunction<UUID, Record1m, RecordAllTime> {

        private transient ValueState<TDigestAccumulator> state;

        @Override
        public void open(Configuration parameters) {
            state = getRuntimeContext().getState(new ValueStateDescriptor<>(
                    "all-time-acc", TDigestAccumulator.class));
        }

        @Override
        public void processElement(Record1m r, Context ctx, Collector<RecordAllTime> out) throws IOException {
            TDigestAccumulator accAll = state.value();
            if (accAll == null) accAll = new TDigestAccumulator();

            accAll.merge(r.acc1m());
            state.update(accAll);

            out.collect(new RecordAllTime(r.deviceId(), accAll));
        }
    }
}
