package org.example.iot.agg.deserializer;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.iot.agg.model.Reading;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.io.IOException;

public class ReadingDeserializationSchema implements DeserializationSchema<Reading> {
    static ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public Reading deserialize(byte[] message) throws IOException {
        return objectMapper.readValue(message, Reading.class);
    }

    @Override public boolean isEndOfStream(Reading r) { return false; }
    @Override public TypeInformation<Reading> getProducedType() {
        return TypeInformation.of(Reading.class);
    }
}
