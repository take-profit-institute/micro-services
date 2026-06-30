package org.profit.candle.trading.reservation.dto;

import org.profit.candle.trading.reservation.entity.ReservationEntity;

/** CAN-004 동등: 취소 시 reserved_balance에서 반환된 금액(BUY만 0보다 큼, SELL은 0). */
public record ReservationCancelResult(
        ReservationEntity reservation, long releasedAmount) {}
