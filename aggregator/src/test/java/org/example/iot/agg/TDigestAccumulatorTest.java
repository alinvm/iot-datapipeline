package org.example.iot.agg;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TDigestAccumulatorTest {
    @Test
    void add_merge_median_roundtrip() {
        var a = new TDigestAccumulator();

        a.add(10); a.add(20); a.add(30);
        assertEquals(3, a.getCount());
        assertEquals(60.0, a.getSum(), 1e-9);
        assertEquals(10.0, a.getMin(), 1e-9);
        assertEquals(30.0, a.getMax(), 1e-9);
        assertEquals(20.0, a.median(), 1.0); // approx

        var b = new TDigestAccumulator();

        b.add(40); b.add(50);
        a.merge(b);
        assertEquals(5, a.getCount());
        assertTrue(a.median() > 20 && a.median() < 40);

        var bytes = a.toBytes();
        var digest = TDigestAccumulator.fromBytes(bytes.array());
        assertNotNull(digest);
        assertEquals(a.median(), digest.quantile(0.5), 2.0); // approximate equality
    }
}