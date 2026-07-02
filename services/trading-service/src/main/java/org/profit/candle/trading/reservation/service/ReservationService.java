package org.profit.candle.trading.reservation.service;

import org.profit.candle.trading.reservation.dto.AmendReservationCommand;
import org.profit.candle.trading.reservation.dto.PlaceReservationCommand;
import org.profit.candle.trading.reservation.dto.ReservationCancelResult;
import org.profit.candle.trading.reservation.entity.ReservationEntity;

import java.util.UUID;

public interface ReservationService {

    /** RSV-001~008: 신규 예약 생성. 가용 가능 금액/보유 수량 검증과 잔고 선점은 이 메서드의 책임이다. */
    ReservationEntity placeReservation(UUID userId, PlaceReservationCommand command);

    /** RSV-016~018: 예약 취소. RESERVED 상태만 가능, 선점된 잔고를 반환한다. */
    ReservationCancelResult cancelReservation(UUID userId, UUID reservationId);

    /**
     * CAN-006/007/008: 예약 정정. 원예약을 취소하고 parent_reservation_id로 연결된
     * 신규 예약을 생성한다. 배치 마감 전 RESERVED 상태에서만 허용된다.
     */
    ReservationEntity amendReservation(UUID userId, AmendReservationCommand command);
}
