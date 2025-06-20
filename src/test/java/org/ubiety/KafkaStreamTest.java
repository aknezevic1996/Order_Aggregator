package org.ubiety;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.test.TestRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ubiety.DTO.Orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KafkaStreamTest {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    public void setUp() {
        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-app");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "testing:1234");
        config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 100);

        StreamsBuilder builder = new StreamsBuilder();

        KafkaStream kafkaStream = new KafkaStream();
        kafkaStream.kStream(builder);

        testDriver = new TopologyTestDriver(builder.build(), config);

        inputTopic = testDriver.createInputTopic(
                "orders",
                Serdes.String().serializer(),
                Serdes.String().serializer()
        );
        outputTopic = testDriver.createOutputTopic(
                "processed-orders",
                Serdes.String().deserializer(),
                Serdes.String().deserializer()
        );
    }

    @AfterEach
    public void tearDown() {
        testDriver.close();
    }

    @Test
    public void testOrderAggregationPerCountry() throws Exception {
        Orders order1 = new Orders("1", Instant.parse("2025-06-19T00:32:00Z"), "US", new BigDecimal("100.50"));
        Orders order2 = new Orders("2", Instant.parse("2025-06-19T00:32:20Z"), "US", new BigDecimal("199.50"));

        inputTopic.pipeInput(null, objectMapper.writeValueAsString(order1), order1.timestamp().toEpochMilli());
        inputTopic.pipeInput(null, objectMapper.writeValueAsString(order2), order2.timestamp().toEpochMilli());

        Orders laterOrder = new Orders("999", Instant.parse("2025-06-19T00:34:00Z"), "US", BigDecimal.ZERO);
        inputTopic.pipeInput(null, objectMapper.writeValueAsString(laterOrder), laterOrder.timestamp().toEpochMilli());

        List<TestRecord<String, String>> results = outputTopic.readRecordsToList();

        assertFalse(results.isEmpty(), "Aggregated output not found");

        boolean foundUS = results.stream()
                .anyMatch(r -> r.getValue().contains("\"country\":\"US\"") && r.getValue().contains("\"total_amount\":300.0"));

        assertTrue(foundUS, "US aggregated total not found");
    }
}
