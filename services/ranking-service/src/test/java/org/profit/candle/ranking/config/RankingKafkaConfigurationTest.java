package org.profit.candle.ranking.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;

class RankingKafkaConfigurationTest {

    /** Listener factory가 Ranking consumer 설정으로 정상 생성되는지 검증한다. */
    @Test
    void createsKafkaListenerContainerFactory() {
        RankingKafkaConfiguration configuration = new RankingKafkaConfiguration();
        ConsumerFactory<String, String> consumerFactory =
                configuration.rankingConsumerFactory("localhost:9092");

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                configuration.kafkaListenerContainerFactory(consumerFactory);

        assertThat(factory).isNotNull();
        assertThat(factory.getContainerProperties().getAckMode())
                .isEqualTo(org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
    }
}
