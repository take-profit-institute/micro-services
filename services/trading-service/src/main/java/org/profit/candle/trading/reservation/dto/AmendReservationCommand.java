package org.profit.candle.trading.reservation.dto;

import org.profit.candle.trading.reservation.entity.ReservationOrderKindValue;
import org.profit.candle.trading.reservation.entity.ReservationTimingValue;

import java.time.LocalDate;
import java.util.UUID;

/**
 * CAN-006/007/008: 예약 정정 명령. BFF API 명세의 AmendReservationBody와 동일하게
 * 모든 필드를 선택(nullable)으로 둔다 — null인 필드는 원예약 값을 그대로 승계한다.
 */
public record AmendReservationCommand(
        UUID reservationId, ReservationTimingValue timing,
        ReservationOrderKindValue kind, Long quantity, Long price,
        LocalDate scheduledDate, String idempotencyKey) {}
