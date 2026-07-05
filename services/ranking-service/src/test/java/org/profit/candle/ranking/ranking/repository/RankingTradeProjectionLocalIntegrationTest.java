package org.profit.candle.ranking.ranking.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.profit.candle.ranking.ranking.entity.ConsumedEventId;
import org.profit.candle.ranking.ranking.entity.RankingParticipant;
import org.profit.candle.ranking.ranking.event.OrderFilledEvent;
import org.profit.candle.ranking.ranking.service.DefaultRankingParticipantProjectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DefaultRankingParticipantProjectionService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_RANKING_TRADE_TEST", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RankingTradeProjectionLocalIntegrationTest {

    private static final UUID USER_ID =
            UUID.fromString("81000000-0000-4000-8000-000000000001");
    private static final int CONCURRENT_ORDER_COUNT = 20;

    private final DefaultRankingParticipantProjectionService projectionService;
    private final RankingParticipantRepository participantRepository;
    private final ConsumedEventRepository consumedEventRepository;

    @Autowired
    RankingTradeProjectionLocalIntegrationTest(
            DefaultRankingParticipantProjectionService projectionService,
            RankingParticipantRepository participantRepository,
            ConsumedEventRepository consumedEventRepository) {
        this.projectionService = projectionService;
        this.participantRepository = participantRepository;
        this.consumedEventRepository = consumedEventRepository;
    }

    /** 실제 PostgreSQL에 ACTIVE 참가자를 준비한다. */
    @BeforeEach
    void setUp() {
        cleanUp();
        participantRepository.save(RankingParticipant.forNewUser(
                USER_ID, "ranking-trade-test", Instant.parse("2026-07-05T06:30:00Z")));
    }

    /** 확인 옵션이 없으면 이 테스트가 사용한 사용자와 이벤트만 삭제한다. */
    @AfterEach
    void tearDown() {
        if (!Boolean.parseBoolean(
                System.getenv().getOrDefault("KEEP_LOCAL_RANKING_TRADE_TEST_DATA", "false"))) {
            cleanUp();
        }
    }

    /** 같은 orderId를 두 번 처리해도 실제 DB 거래 횟수는 한 번만 증가하는지 검증한다. */
    @Test
    @Order(1)
    void duplicateOrderFilledIncrementsTradeCountOnce() {
        UUID orderId = orderId(1);
        OrderFilledEvent event = event(orderId);

        projectionService.projectFilledOrder(event);
        projectionService.projectFilledOrder(event);

        assertThat(participantRepository.findById(USER_ID).orElseThrow().tradeCount()).isEqualTo(1);
        assertThat(consumedEventRepository.existsById(
                new ConsumedEventId("trading-service", orderId))).isTrue();
    }

    /** 서로 다른 orderId가 동시에 처리돼도 DB 원자 증가로 모든 체결이 반영되는지 검증한다. */
    @Test
    @Order(2)
    void concurrentOrderFilledDoesNotLoseTradeCount() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int sequence = 1; sequence <= CONCURRENT_ORDER_COUNT; sequence++) {
                OrderFilledEvent event = event(orderId(sequence));
                futures.add(executor.submit(() -> projectionService.projectFilledOrder(event)));
            }
            for (var future : futures) {
                future.get();
            }
        }

        assertThat(participantRepository.findById(USER_ID).orElseThrow().tradeCount())
                .isEqualTo(CONCURRENT_ORDER_COUNT);
    }

    /** 테스트 전용 주문 ID로 현재 Trading OrderFilled payload를 만든다. */
    private OrderFilledEvent event(UUID orderId) {
        return new OrderFilledEvent(
                orderId.toString(), USER_ID.toString(), "005930", "BUY",
                80_000L, 1L, 10L, 0L, 80_010L);
    }

    /** 테스트 순서를 예측할 수 있는 전용 주문 UUID를 만든다. */
    private UUID orderId(int sequence) {
        return UUID.fromString("82000000-0000-4000-8000-%012d".formatted(sequence));
    }

    /** 이 테스트 scope의 소비 이력과 참가자만 정리한다. */
    private void cleanUp() {
        for (int sequence = 1; sequence <= CONCURRENT_ORDER_COUNT; sequence++) {
            consumedEventRepository.deleteById(
                    new ConsumedEventId("trading-service", orderId(sequence)));
        }
        participantRepository.deleteById(USER_ID);
    }
}
