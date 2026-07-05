package org.profit.candle.batch.trading.client;

import java.time.LocalDate;
import java.util.List;

public interface TradingBatchClient {

    int processPreviousCloseReservations(LocalDate scheduledDate);

    int processOpenLimitReservations(LocalDate scheduledDate);

    int expirePendingOrders();

    List<String> listStaleConvertingReservations(LocalDate scheduledDate);

    boolean failStaleConvertingReservation(String reservationId);

    int processTodayCloseReservations(LocalDate scheduledDate);

    List<String> listExpirableReservations(LocalDate scheduledDate);

    boolean expireReservation(String reservationId);
}
