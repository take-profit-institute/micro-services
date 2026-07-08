package org.profit.candle.trading.account.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.trading.account.entity.AccountEntity;
import org.profit.candle.trading.account.entity.ConsumedEvent;
import org.profit.candle.trading.account.repository.AccountRepository;
import org.profit.candle.trading.account.repository.ConsumedEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountUserCreatedEventConsumerTest {

    @Mock private ConsumedEventRepository consumedEventRepository;
    @Mock private AccountRepository accountRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AccountUserCreatedEventConsumer consumer;

    private final UUID eventId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new AccountUserCreatedEventConsumer(consumedEventRepository, accountRepository, objectMapper);
    }

    private String validPayloadJson() {
        return """
                {"eventId":"%s","eventType":"UserCreated","eventVersion":1,
                 "userId":"%s","email":"demo@candle.app","occurredAt":"2026-07-06T00:00:00Z"}
                """.formatted(eventId, userId);
    }

    @Test
    @DisplayName("최초 수신 시 계좌를 생성하고 consumed_events에 기록한다")
    void shouldCreateAccountAndRecordConsumedEventOnFirstReceipt() {
        when(consumedEventRepository.existsById(eventId)).thenReturn(false);

        consumer.onUserCreated(validPayloadJson());

        ArgumentCaptor<AccountEntity> accountCaptor = ArgumentCaptor.forClass(AccountEntity.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getUserId()).isEqualTo(userId);

        ArgumentCaptor<ConsumedEvent> eventCaptor = ArgumentCaptor.forClass(ConsumedEvent.class);
        verify(consumedEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventId()).isEqualTo(eventId);
    }

    @Test
    @DisplayName("이미 처리된 eventId면 계좌 생성 없이 skip한다")
    void shouldSkipWhenEventAlreadyConsumed() {
        when(consumedEventRepository.existsById(eventId)).thenReturn(true);

        consumer.onUserCreated(validPayloadJson());

        verify(accountRepository, never()).save(any());
        verify(consumedEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("역직렬화 실패 시 예외 없이 조용히 반환한다")
    void shouldReturnSilentlyOnDeserializationFailure() {
        assertThatCode(() -> consumer.onUserCreated("not-a-json"))
                .doesNotThrowAnyException();

        verifyNoInteractions(consumedEventRepository, accountRepository);
    }

    @Test
    @DisplayName("계좌가 이미 존재해도(경합) consumed_events는 기록하고 정상 종료한다")
    void shouldRecordConsumedEventEvenWhenAccountAlreadyExistsDueToRace() {
        when(consumedEventRepository.existsById(eventId)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(accountRepository).save(any(AccountEntity.class));

        assertThatCode(() -> consumer.onUserCreated(validPayloadJson()))
                .doesNotThrowAnyException();

        verify(consumedEventRepository).save(any(ConsumedEvent.class));
    }
}
