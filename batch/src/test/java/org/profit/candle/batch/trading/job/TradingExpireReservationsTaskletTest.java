package org.profit.candle.batch.trading.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.trading.client.TradingBatchClient;
import org.profit.candle.batch.trading.exception.TradingBatchErrorCode;
import org.profit.candle.batch.trading.exception.TradingBatchException;
import org.profit.candle.batch.trading.policy.TradingBatchRetryExecutor;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

class TradingExpireReservationsTaskletTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);

    /** 목록의 모든 예약을 호출하고 false 응답은 정상적으로 건너뛴다. */
    @Test
    void expiresEveryListedReservationAndSkipsChangedState() {
        TradingBatchClient client = mock(TradingBatchClient.class);
        when(client.listExpirableReservations(DATE))
                .thenReturn(List.of("reservation-1", "reservation-2"));
        when(client.expireReservation("reservation-1")).thenReturn(true);
        when(client.expireReservation("reservation-2")).thenReturn(false);
        TradingExpireReservationsTasklet tasklet = tasklet(client);

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(client).expireReservation("reservation-1");
        verify(client).expireReservation("reservation-2");
    }

    /** 건별 만료 중 일시적 오류가 발생하면 해당 예약만 다시 호출한다. */
    @Test
    void retriesOnlyFailedReservation() {
        TradingBatchClient client = mock(TradingBatchClient.class);
        when(client.listExpirableReservations(DATE))
                .thenReturn(List.of("reservation-1"));
        when(client.expireReservation("reservation-1"))
                .thenThrow(retryableFailure())
                .thenReturn(true);
        TradingExpireReservationsTasklet tasklet = tasklet(client);

        tasklet.execute(null, null);

        verify(client, times(2)).expireReservation("reservation-1");
    }

    /** 테스트 대상 Tasklet을 고정 거래일과 실제 재시도 정책으로 생성한다. */
    private TradingExpireReservationsTasklet tasklet(TradingBatchClient client) {
        return new TradingExpireReservationsTasklet(
                DATE,
                client,
                new TradingBatchRetryExecutor()
        );
    }

    /** 재시도 가능한 Trading 외부 오류를 생성한다. */
    private TradingBatchException retryableFailure() {
        return new TradingBatchException(
                TradingBatchErrorCode.EXTERNAL_CLIENT_RETRYABLE,
                new IllegalStateException("temporary failure")
        );
    }
}
