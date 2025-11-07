package org.example.iot.agg;

import org.apache.flink.api.common.functions.AggregateFunction;
import org.example.iot.agg.model.Reading;

public class TDigestAggregateFunction
        implements AggregateFunction<Reading, TDigestAccumulator, TDigestAccumulator> {

    @Override
    public TDigestAccumulator createAccumulator() { return new TDigestAccumulator(); }

    @Override
    public TDigestAccumulator add(Reading reading, TDigestAccumulator acc) {
        // update streaming stats + t-digest
        acc.add(reading.value());
        return acc;
    }

    @Override
    public TDigestAccumulator getResult(TDigestAccumulator acc) {
        // final per-window accumulator (count/sum/min/max/digest)
        return acc;
    }

    @Override
    public TDigestAccumulator merge(TDigestAccumulator a, TDigestAccumulator b) {
        // combine partials from different shards/subtasks
        a.merge(b);
        return a;
    }
}

