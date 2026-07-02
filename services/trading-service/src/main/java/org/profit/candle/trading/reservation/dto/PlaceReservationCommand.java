package org.profit.candle.trading.reservation.dto;

import org.profit.candle.trading.reservation.entity.ReservationOrderKindValue;
import org.profit.candle.trading.reservation.entity.ReservationSideValue;
import org.profit.candle.trading.reservation.entity.ReservationTimingValue;

import java.time.LocalDate;

public record PlaceReservationCommand(
        String symbol, ReservationSideValue side, ReservationTimingValue timing,
        ReservationOrderKindValue kind, long quantity, Long price,
        LocalDate scheduledDate, String idempotencyKey) {}
