package org.example.iot.query.repo;


import com.datastax.oss.driver.api.core.CqlSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;

@Configuration
public class CassandraConfig {

    @Bean
    public CqlSession session() {
        return CqlSession.builder()
                .addContactPoint(new InetSocketAddress(
                        System.getenv().getOrDefault("CASSANDRA_HOST", "cassandra"), 9042))
                .withKeyspace(System.getenv().getOrDefault("CASSANDRA_KEYSPACE", "iot"))
                .withLocalDatacenter("datacenter1")
                .build();
    }
}
