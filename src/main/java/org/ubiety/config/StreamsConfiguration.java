package org.ubiety.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.Serdes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.config.TopicBuilder;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafkaStreams
public class StreamsConfiguration {

    public static final String ORDERS = "orders";
    public static final String PROCESSED_ORDERS = "processed-orders";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.streams.application-id}")
    private String applicationId;

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kafkaStreamsConfiguration() {
        Map<String, Object> props = new HashMap<>();
        props.put(org.apache.kafka.streams.StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        props.put(org.apache.kafka.streams.StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(org.apache.kafka.streams.StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(org.apache.kafka.streams.StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(org.apache.kafka.streams.StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000);

        return new KafkaStreamsConfiguration(props);
    }

    @Bean
    public NewTopic processedOrdersTopic() {
        return TopicBuilder.name(PROCESSED_ORDERS)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ordersTopic() {
        return TopicBuilder.name(ORDERS)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
