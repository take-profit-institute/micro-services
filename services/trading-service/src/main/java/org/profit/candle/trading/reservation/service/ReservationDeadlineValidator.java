package org.profit.candle.trading.reservation.service;

import org.profit.candle.trading.reservation.entity.ReservationTimingValue;
import org.profit.candle.trading.reservation.exception.ReservationErrorCode;
import org.profit.candle.trading.reservation.exception.ReservationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 예약 주문 접수 마감 시간 검증 (RSV-006~008).
 *
 * timing별 KST 접수 마감 시간 이후에는 예약 주문을 접수할 수 없다.
 * TradingHoursValidator와 동일하게 Clock을 주입받아 단위 테스트에서
 * 시간을 모킹할 수 있게 한다.
 *
 * <pre>
 * PREV_CLOSE : 08:25 이후 접수 불가 (BAT-002)
 * OPEN       : 08:50 이후 접수 불가 (BAT-004)
 * TODAY_CLOSE: 15:30 이후 접수 불가 (BAT-009)
 * </pre>
 *
 * 마감 경계는 exclusive — {@code !now.isBefore(deadline)}이면 마감으로 처리한다.
 * TradingHoursValidator의 15:30 경계({@code !now.isBefore(MARKET_CLOSE)})와 동일한 방식이다.
 */
@Component
public class ReservationDeadlineValidator {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final LocalTime PREV_CLOSE_DEADLINE  = LocalTime.of(8, 25);
    private static final LocalTime OPEN_DEADLINE        = LocalTime.of(8, 50);
    private static final LocalTime TODAY_CLOSE_DEADLINE = LocalTime.of(15, 30);

    private final Clock clock;

    public ReservationDeadlineValidator(Clock clock) {
        this.clock = clock;
    }

    /**
     * timing에 따른 접수 마감 시간을 검증한다.
     * 마감 시간 이후면 {@link ReservationException}을 던진다.
     */
    public void requireBeforeDeadline(ReservationTimingValue timing) {
        LocalTime now = LocalTime.now(clock.withZone(KST));

        switch (timing) {
            case PREV_CLOSE -> {
                if (!now.isBefore(PREV_CLOSE_DEADLINE)) {
                    throw new ReservationException(ReservationErrorCode.PREV_CLOSE_DEADLINE_PASSED);
                }
            }
            case OPEN -> {
                if (!now.isBefore(OPEN_DEADLINE)) {
                    throw new ReservationException(ReservationErrorCode.OPEN_DEADLINE_PASSED);
                }
            }
            case TODAY_CLOSE -> {
                if (!now.isBefore(TODAY_CLOSE_DEADLINE)) {
                    throw new ReservationException(ReservationErrorCode.TODAY_CLOSE_DEADLINE_PASSED);
                }
            }
        }
    }
}