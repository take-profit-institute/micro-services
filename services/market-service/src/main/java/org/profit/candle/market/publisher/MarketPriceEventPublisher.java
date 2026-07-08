package org.profit.candle.market.publisher;

import lombok.extern.slf4j.Slf4j;
import org.profit.candle.market.dto.message.MarketPriceEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class MarketPriceEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String topic;

    public MarketPriceEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${market.price.topic:market.price.v1}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(String symbol, long price) {
        if (symbol == null || symbol.isBlank() || price <= 0) {
            return;
        }
        try {
            kafkaTemplate.send(topic, symbol, objectMapper.writeValueAsString(new MarketPriceEvent(symbol, price)));
        } catch (RuntimeException e) {
            log.warn("Failed to publish market price event. symbol={}", symbol, e);
        }
    }
}
