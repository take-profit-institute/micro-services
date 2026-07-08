package org.profit.candle.market.orderbook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class KafkaOrderBookPublisher implements OrderBookPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String topic;

    public KafkaOrderBookPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${market.order-book.topic:market.order-book.v1}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(OrderBookSnapshot snapshot) {
        try {
            kafkaTemplate.send(topic, snapshot.symbol(), objectMapper.writeValueAsString(snapshot));
        } catch (RuntimeException e) {
            log.warn("Failed to publish order book. symbol={}", snapshot.symbol(), e);
        }
    }
}
