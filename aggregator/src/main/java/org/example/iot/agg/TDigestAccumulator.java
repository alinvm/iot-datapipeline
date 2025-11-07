package org.example.iot.agg;

import com.tdunning.math.stats.MergingDigest;

import java.nio.ByteBuffer;
import java.util.Objects;

public class TDigestAccumulator {
    private long count;
    private double sum;
    private double min = Double.POSITIVE_INFINITY;
    private double max = Double.NEGATIVE_INFINITY;
    private final MergingDigest digest;

    public TDigestAccumulator() {
        // compression 100 is a good default for p50 accuracy
        this.digest = new MergingDigest(100);
    }

    public long getCount() {
        return count;
    }

    public double getSum() {
        return sum;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public void add(double v) {
        count++;
        sum += v;
        if (v < min) min = v;
        if (v > max) max = v;
        digest.add(v);
    }

    public void merge(TDigestAccumulator other) {
        if (other == null || other.count == 0) return;
        this.count += other.count;
        this.sum   += other.sum;
        this.min    = Math.min(this.min, other.min);
        this.max    = Math.max(this.max, other.max);
        // Merge the centroids
        this.digest.add(other.digest);
    }

    public double median() {
        return count == 0 ? Double.NaN : digest.quantile(0.5);
    }

    /** Serialize to a compact byte[] suitable for Cassandra blob columns. */
    public ByteBuffer toBytes() {
        int size = digest.smallByteSize();
        ByteBuffer buf = ByteBuffer.allocate(size);
        digest.asSmallBytes(buf);
        buf.flip();                 // prepare for reading
        return buf;                 // <-- ready to bind to Cassandra
    }

    /** Deserialize a TDigest from the compact byte[] representation. */
    public static MergingDigest fromBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        return MergingDigest.fromBytes(buf);
    }
}
