package org.profit.candle.batch.trading.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.trading.client.TradingBatchClient;
import org.profit.candle.batch.trading.exception.TradingBatchErrorCode;
import org.profit.candle.batch.trading.exception.TradingBatchException;
import org.profit.candle.batch.trading.policy.TradingBatchRetryExecutor;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

class TradingReservationProcessTaskletTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);

    /** PREVIOUS_CLOSE 작업이 전일 종가 예약 RPC만 호출하는지 검증한다. */
    @Test
    void processesPreviousCloseReservations() {
        TradingBatchClient client = mock(TradingBatchClient.class);
        when(client.processPreviousCloseReservations(DATE)).thenReturn(3);
        TradingReservationProcessTasklet tasklet = tasklet(
                TradingReservationProcessTasklet.Operation.PREVIOUS_CLOSE,
                client
        );

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(client).processPreviousCloseReservations(DATE);
    }

    /** OPEN_LIMIT 작업이 시가 지정가 예약 RPC만 호출하는지 검증한다. */
    @Test
    void processesOpenLimitReservations() {
        TradingBatchClient client = mock(TradingBatchClient.class);
        when(client.processOpenLimitReservations(DATE)).thenReturn(4);
        TradingReservationProcessTasklet tasklet = tasklet(
                TradingReservationProcessTasklet.Operation.OPEN_LIMIT,
                client
        );

        tasklet.execute(null, null);

        verify(client).processOpenLimitReservations(DATE);
    }

    /** 일시적 Trading 오류가 발생하면 동일 작업만 다시 호출하는지 검증한다. */
    @Test
    void retriesOnlySelectedOperation() {
        TradingBatchClient client = mock(TradingBatchClient.class);
        when(client.processOpenLimitReservations(DATE))
                .thenThrow(retryableFailure())
                .thenReturn(4);
        TradingReservationProcessTasklet tasklet = tasklet(
                TradingReservationProcessTasklet.Operation.OPEN_LIMIT,
                client
        );

        tasklet.execute(null, null);

        verify(client, times(2)).processOpenLimitReservations(DATE);
    }

    /** 테스트 대상 Tasklet을 실제 재시도 정책과 함께 생성한다. */
    private TradingReservationProcessTasklet tasklet(
            TradingReservationProcessTasklet.Operation operation,
            TradingBatchClient client
    ) {
        return new TradingReservationProcessTasklet(
                DATE,
                operation,
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
