package org.profit.candle.market.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Kafka {@code ProducerConfig} 정적 초기화를 애플리케이션 기동 스레드에서 미리 강제한다.
 *
 * <p>배경: market-service 는 키움 실시간 시세를 JDK {@code java.net.http} HttpClient 의 WebSocket
 * 워커 스레드에서 처리한다. 그 스레드의 context classloader 는 Spring Boot fat-jar 의
 * {@code LaunchedClassLoader} 가 아니라서, Kafka 프로듀서를 그 스레드에서 "최초로" 생성하면
 * {@code SaslConfigs} 의 {@code sasl.oauthbearer.jwt.retriever.class} 기본값
 * ({@code org.apache.kafka.common.security.oauthbearer.DefaultJwtRetriever}) 클래스를 못 찾아
 * {@code ExceptionInInitializerError} 로 {@code ProducerConfig} 가 영구 오염된다
 * (이후 모든 발행이 {@code NoClassDefFoundError}).
 *
 * <p>여기서 기동 스레드(정상 classloader)로 {@code ProducerConfig} 를 먼저 초기화해 두면,
 * 이후 어떤 스레드에서 프로듀서를 처음 만들어도 정적 초기화는 이미 끝나 있어 재발하지 않는다.
 */
@Slf4j
@Component
public class KafkaProducerWarmup {

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpProducerConfig() {
        try {
            Class.forName(
                    "org.apache.kafka.clients.producer.ProducerConfig",
                    true,
                    getClass().getClassLoader());
            log.info("Kafka ProducerConfig 사전 초기화 완료 (classloader warm-up)");
        } catch (Throwable t) {
            log.warn("Kafka ProducerConfig 사전 초기화 실패 — 프로듀서 최초 생성 스레드에 주의 필요", t);
        }
    }
}
