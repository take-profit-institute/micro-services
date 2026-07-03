package org.profit.candle.trading.account.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.trading.account.entity.AccountEntity;
import org.profit.candle.trading.account.event.dto.UserCreatedPayload;
import org.profit.candle.trading.account.entity.ConsumedEvent;
import org.profit.candle.trading.account.repository.ConsumedEventRepository;
import org.profit.candle.trading.account.repository.AccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * auth.user-created.v1 이벤트 수신 후 계좌를 자동 생성한다. (ACC-001)
 *
 * <p>멱등성: consumed_events.event_id(PK)로 중복 수신을 막는다 —
 * Kafka→Consumer 경계는 event_id만으로 충분하다(request_hash 불필요,
 * 클라이언트→서버 멱등성 키 설계 §4 구분 기준).</p>
 *
 * <p>초기 시드머니(1억원)는 {@link AccountEntity#create(java.util.UUID)}
 * 팩토리에서 cash_krw에 즉시 반영된다. account_deposits(입금이력) 테이블이
 * 아직 없어 지급 근거를 별도로 기록하지 못하는 상태 — DEP-001 별도 이슈로 보완 예정.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountUserCreatedEventConsumer {

    private final ConsumedEventRepository consumedEventRepository;
    private final AccountRepository accountRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "auth.user-created.v1")
    @Transactional
    public void onUserCreated(String rawPayload) {
        UserCreatedPayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, UserCreatedPayload.class);
        } catch (Exception e) {
            log.error("UserCreated 이벤트 역직렬화 실패. payload={}", rawPayload, e);
            return;
        }

        if (consumedEventRepository.existsById(payload.eventId())) {
            log.info("이미 처리된 이벤트 skip. eventId={}", payload.eventId());
            return;
        }

        try {
            accountRepository.save(AccountEntity.create(payload.userId()));
            consumedEventRepository.save(new ConsumedEvent(payload.eventId(), payload.eventType()));
            log.info("계좌 자동 생성 완료. userId={}, eventId={}", payload.userId(), payload.eventId());
        } catch (DataIntegrityViolationException e) {
            // 계좌가 이미 존재하는 경우(재시도, 중복 전송 등) — consumed_events만 기록하고 정상 처리
            log.warn("계좌 이미 존재. userId={}, eventId={}", payload.userId(), payload.eventId());
            consumedEventRepository.save(new ConsumedEvent(payload.eventId(), payload.eventType()));
        }
    }
}