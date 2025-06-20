package org.ubiety;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.ubiety.DTO.AggregatedOrders;
import org.ubiety.DTO.Orders;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;

import static org.ubiety.config.StreamsConfiguration.ORDERS;
import static org.ubiety.config.StreamsConfiguration.PROCESSED_ORDERS;

@Component
@Slf4j
public class KafkaStream {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    @Bean
    public KStream<String, String> kStream(StreamsBuilder builder) {

        KStream<String, String> source = builder.stream(ORDERS);

        KGroupedStream<String, String> grouped = source
                .selectKey((key, value) -> {
                    try {
                        Orders o = objectMapper.readValue(value, Orders.class);
                        return o.country();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .peek((key, value) ->
                        log.debug("Consumed message: key={}, value={}", key, value)
                )
                .filter((key, value) -> key != null && value != null)
                .groupByKey(Grouped.with(Serdes.String(), Serdes.String()));

        KTable<Windowed<String>, Double> amountPerCountry = grouped
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
                .aggregate(
                        () -> 0.0,
                        (key, value, agg) -> {
                            try {
                                Orders o = objectMapper.readValue(value, Orders.class);
                                return o.amount().add(BigDecimal.valueOf(agg)).doubleValue();
                            } catch (Exception e) {
                                throw new RuntimeException("Unable to parse Order message", e);
                            }
                        },
                        Materialized.with(Serdes.String(), Serdes.Double())
                )
                .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()));

        amountPerCountry
                .toStream()
                .mapValues((windowedKey, total) -> {
                    String country = windowedKey.key();
                    String windowStart = windowedKey.window().startTime()
                            .atZone(ZoneId.of("UTC-05:00")).toLocalDateTime().toString();
                    AggregatedOrders result = new AggregatedOrders(country, windowStart, BigDecimal.valueOf(total));
                    try {
                        return objectMapper.writeValueAsString(result);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("Could not serialize processed order", e);
                    }
                })
                .selectKey((windowedKey, value) -> windowedKey.key())
                .peek((key, value) ->
                        log.info("Publishing message: key={}, value={}", key, value)
                )
                .to(PROCESSED_ORDERS, Produced.with(Serdes.String(), Serdes.String()));

        return source;
    }
}