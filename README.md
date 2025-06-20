# Kafka Streams Order Aggregator

## Description

This project is a Kafka Streams-based Java 17 Spring Boot application that consumes JSON messages
from an `orders` topic, aggregates total order amounts per country in 1-minute tumbling windows,
and publishes the results to a `processed-orders` topic. It includes a Docker Compose setup that
brings up Kafka, Zookeeper, and the aggregator app, alongside a Python script to publish `orders`.

---

## Requirements

Docker and Python 3 are required to run this application. Bash shell is needed to run the shell
script (use WSL if on Windows). Python packages may need to be installed with `pip`.


## How to Use

### Building and Running the App

**Run the script**

Run these commands within the root project directory:

```bash
chmod +x streaming_setup.sh

python3 -m venv .venv #if needed

source .venv/bin/activate #if needed

./streaming_setup.sh kafka
```

This will:
* Make the script executable
* Create and activate the virtualenv (if needed)
* Start Zookeeper, Kafka, and the Java aggregator app
* Publish a stream of orders via Python

**Verify output**

View the aggregated results within Docker:

```bash
docker exec -it kafka

kafka-console-consumer.sh --bootstrap-server kafka:9093 --topic processed-orders --from-beginning
```

Note that `processed-orders` messages will first appear after one minute of processing
has passed, and then every minute after that until all orders are consumed.

---

## Configuration and Design Decisions

### Spring Boot

I opted to use Spring Framework as it has good support for Kafka Streams,
offering more concise configuration and cleaner code with its use of dependency
injections and component auto-wiring.

### Kafka Streams Topology

```java
TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1))
```

This sets a tumbling window of 1 minute. The grace period is disabled, meaning
events must arrive on time and any late events are dropped. A grace period can
be configured with `TimeWindows.ofSizeAndGrace()`.

```java
.suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
```

This emits only the final aggregation (grouped by country) per window. If omitted,
it would publish intermediate `processed-orders` messages which show the summation
of aggregates within the window. I understood the instructions to only publish the
final results.

Additionally, a buffer config can be provided to prevent an `OutOfMemoryError`. Here,
I made it unbounded as the buffer will not be large in this environment. If high
volume is expected, a `StrictBufferConfig` can be used to set a maximum amount of
suppressed records and emit them early if the limit is reached.

### Topics

`orders` (input): Consumes JSON messages such as:

```json
{
  "order_id": "1", 
  "timestamp": "2025-06-19T05:32:00.000105-05:00", 
  "country": "US", 
  "amount": 100.5
}
```

`processed-orders` (output): Emits:

```json
{
  "country": "US", 
  "window_start": "2025-06-19T05:32", 
  "total_amount": 576.24
}
```

Timestamps are of different formats as `window_start` is serialized from a `LocalDateTime` object.

### Configuration Class

```java
@Configuration
@EnableKafkaStreams
public class StreamsConfig {
    //...
}
```

This class registers Kafka Streams config such as (de)serializers, bootstrap servers and 
commit intervals. It also reads environment variables from `application.yml`.

```java
@Bean
public NewTopic ordersTopic() { 
    //... 
}
@Bean
public NewTopic processedOrdersTopic() { 
    //... 
}
```

Spring Kafka auto-creates these topics on app startup. This prevented issues when starting 
the app before topics are created, and will wait for messages to come in. Also:

```java
.to(PROCESSED_ORDERS, Produced.with(Serdes.String(), Serdes.String()))
```

requires whatever topic being published to (`processed-orders` in this case) to be
manually created before it is used, otherwise it will silently drop the messages.

---

## Assumptions

* Processed order timestamps are converted to Central US time from UTC to match the produced `orders` timestamps
* Amounts in input `orders` messages are assumed not to be null.
* Only one partition per topic, as it uses default broker settings.
* Aggregation is based on the message arrival time, windowed by minute processed.
