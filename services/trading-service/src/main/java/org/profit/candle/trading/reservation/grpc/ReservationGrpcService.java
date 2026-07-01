package org.profit.candle.trading.reservation.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.profit.candle.proto.trading.v1.*;
import org.profit.candle.trading.account.exception.AccountErrorCode;
import org.profit.candle.trading.account.exception.AccountException;
import org.profit.candle.trading.reservation.dto.AmendReservationCommand;
import org.profit.candle.trading.reservation.dto.PlaceReservationCommand;
import org.profit.candle.trading.reservation.dto.ReservationCancelResult;
import org.profit.candle.trading.reservation.entity.*;
import org.profit.candle.trading.reservation.event.ReservationIdempotencyOperations;
import org.profit.candle.trading.reservation.exception.ReservationErrorCode;
import org.profit.candle.trading.reservation.exception.ReservationException;
import org.profit.candle.trading.reservation.repository.ReservationRepository;
import org.profit.candle.trading.reservation.service.ReservationBatchService;
import org.profit.candle.trading.reservation.service.ReservationService;
import org.profit.candle.trading.support.idempotency.IdempotencyContext;
import org.profit.candle.trading.support.idempotency.IdempotencyExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * ReservationService gRPC 엔드포인트.
 *
 * 쓰기 RPC(PlaceReservation/CancelReservation/AmendReservation)는 {@link IdempotencyExecutor}를
 * 명시적으로 호출해 멱등성 처리(스펙 §5)를 보이게 한다. 읽기는 바로 조회한다.
 *
 * <p>ListReservations는 페이징 없이 전체 목록을 반환한다 — BFF API 명세(GET /api/account/reservations)에
 * status 필터만 있고 limit/offset/page 파라미터가 없으며, 유저당 계좌가 1개라 데이터 규모도 작다.
 * order/account의 목록 RPC와 동일한 패턴이다.</p>
 *
 * <p>ReservationException/AccountException → gRPC Status 매핑을 이 클래스가 직접 한다.
 * OrderGrpcService와 동일한 이유로 AccountErrorCode 매핑이 여기에도 중복 존재한다 —
 * ErrorCode가 전송 계층(gRPC)을 모르게 한다는 원칙(컨벤션 8장)을 지키기 위해 의도적으로
 * 각 GrpcService가 따로 갖는다. support/ 공통 변환 계층(인터셉터/AOP)이 생기면 이 중복은
 * 해소된다 — 관련 제안 이슈 #{이슈번호}.</p>
 *
 * <p>proto {@code ReservationStatus}는 RESERVED/EXECUTED/CANCELLED 3종만 정의되어 있어
 * 도메인 enum의 CONVERTING/FAILED/EXPIRED는 toProtoStatus()에서 별도 처리가 필요하다.
 * 우선 CONVERTING은 RESERVED로, FAILED/EXPIRED는 CANCELLED로 잠정 매핑했다 — proto에
 * 해당 값을 추가하는 게 맞는 방향이며, 이는 후속 확인이 필요하다.</p>
 *
 * <p>배치 전용 RPC(ProcessOpenLimitReservations/MarkReservationConverted)는 requireActor()를
 * 호출하지 않는다 — 배치 서비스는 시스템 권한으로 호출하며, order의 ExpirePendingOrders와
 * 동일한 컨벤션을 따른다. 네트워크 경계(내부망)로 보호한다.</p>
 */
@Component
@RequiredArgsConstructor
public class ReservationGrpcService extends ReservationServiceGrpc.ReservationServiceImplBase {

    private final ReservationService reservationService;
    private final ReservationBatchService reservationBatchService;
    private final ReservationRepository reservationRepository;
    private final IdempotencyExecutor idempotencyExecutor;
    private final ReservationIdempotencyOperations idempotencyOperations;

    // ── 읽기 ──────────────────────────────────────────────────────────
    @Override
    public void listReservations(ListReservationsRequest request,
                                 StreamObserver<ListReservationsResponse> observer) {
        UUID actor = requireActor(request.getUserId());
        ListReservationsResponse.Builder response = ListReservationsResponse.newBuilder();
        var reservations = request.getStatus() == ReservationStatus.RESERVATION_STATUS_UNSPECIFIED
                ? reservationRepository.findByUserIdOrderByCreatedAtDesc(actor)
                : reservationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(actor, toStatus(request.getStatus()));
        reservations.forEach(reservation -> response.addReservations(toProto(reservation)));
        observer.onNext(response.build());
        observer.onCompleted();
    }

    // ── 쓰기 (멱등) ───────────────────────────────────────────────────
    @Override
    public void placeReservation(PlaceReservationRequest request,
                                 StreamObserver<PlaceReservationResponse> observer) {
        try {
            UUID actor = requireActor(request.getUserId());
            String idempotencyKey = currentIdempotencyKey();
            Long price = request.getPrice() == 0 ? null : request.getPrice();
            LocalDate scheduledDate = null;
            if (!request.getScheduledDate().isBlank()) {
                scheduledDate = parseScheduledDate(request.getScheduledDate(), observer);
                if (scheduledDate == null) return;
            }

            var command = new PlaceReservationCommand(
                    request.getSymbol(), toSide(request.getSide()), toTiming(request.getTiming()),
                    toKind(request.getKind()), request.getQuantity(), price, scheduledDate, idempotencyKey);

            PlaceReservationResponse response = idempotencyExecutor.execute(
                    request,
                    PlaceReservationResponse.parser(),
                    idempotencyOperations,
                    () -> PlaceReservationResponse.newBuilder()
                            .setReservation(toProto(reservationService.placeReservation(actor, command)))
                            .build());

            observer.onNext(response);
            observer.onCompleted();
        } catch (ReservationException e) {
            observer.onError(toGrpcException((ReservationErrorCode) e.errorCode()));
        } catch (AccountException e) {
            observer.onError(toGrpcException((AccountErrorCode) e.errorCode()));
        } catch (DataIntegrityViolationException e) {
            observer.onError(toGrpcException(ReservationErrorCode.DUPLICATE_PENDING_RESERVATION));
        }
    }

    @Override
    public void cancelReservation(CancelReservationRequest request,
                                  StreamObserver<CancelReservationResponse> observer) {
        try {
            UUID actor = requireActor(request.getUserId());

            CancelReservationResponse response = idempotencyExecutor.execute(
                    request,
                    CancelReservationResponse.parser(),
                    idempotencyOperations,
                    () -> {
                        ReservationCancelResult result = reservationService.cancelReservation(
                                actor, UUID.fromString(request.getReservationId()));
                        return CancelReservationResponse.newBuilder()
                                .setReservation(toProto(result.reservation()))
                                .setReleasedAmount(result.releasedAmount())
                                .build();
                    });

            observer.onNext(response);
            observer.onCompleted();
        } catch (ReservationException e) {
            observer.onError(toGrpcException((ReservationErrorCode) e.errorCode()));
        } catch (AccountException e) {
            observer.onError(toGrpcException((AccountErrorCode) e.errorCode()));
        } catch (IllegalArgumentException e) {
            observer.onError(toGrpcException(ReservationErrorCode.INVALID_ID_FORMAT));
        }
    }

    @Override
    public void amendReservation(AmendReservationRequest request,
                                 StreamObserver<AmendReservationResponse> observer) {
        try {
            UUID actor = requireActor(request.getUserId());
            String idempotencyKey = currentIdempotencyKey();

            ReservationTimingValue timing = request.getTiming() == ReservationTiming.RESERVATION_TIMING_UNSPECIFIED
                    ? null : toTiming(request.getTiming());
            ReservationOrderKindValue kind = request.getKind() == OrderKind.ORDER_KIND_UNSPECIFIED
                    ? null : toKind(request.getKind());
            Long quantity = request.getQuantity() == 0 ? null : request.getQuantity();
            Long price = request.getPrice() == 0 ? null : request.getPrice();
            LocalDate scheduledDate = null;
            if (!request.getScheduledDate().isBlank()) {
                scheduledDate = parseScheduledDate(request.getScheduledDate(), observer);
                if (scheduledDate == null) return;
            }

            var command = new AmendReservationCommand(
                    UUID.fromString(request.getReservationId()), timing, kind, quantity, price,
                    scheduledDate, idempotencyKey);

            AmendReservationResponse response = idempotencyExecutor.execute(
                    request,
                    AmendReservationResponse.parser(),
                    idempotencyOperations,
                    () -> AmendReservationResponse.newBuilder()
                            .setReservation(toProto(reservationService.amendReservation(actor, command)))
                            .build());

            observer.onNext(response);
            observer.onCompleted();
        } catch (ReservationException e) {
            observer.onError(toGrpcException((ReservationErrorCode) e.errorCode()));
        } catch (AccountException e) {
            observer.onError(toGrpcException((AccountErrorCode) e.errorCode()));
        } catch (DataIntegrityViolationException e) {
            observer.onError(toGrpcException(ReservationErrorCode.DUPLICATE_PENDING_RESERVATION));
        } catch (IllegalArgumentException e) {
            observer.onError(toGrpcException(ReservationErrorCode.INVALID_ID_FORMAT));
        }
    }

    // ── 배치 전용 RPC ─────────────────────────────────────────────────
    @Override
    public void processOpenLimitReservations(ProcessOpenLimitReservationsRequest request,
                                             StreamObserver<ProcessOpenLimitReservationsResponse> observer) {
        try {
            if (request.getScheduledDate().isBlank()) {
                observer.onError(toGrpcException(ReservationErrorCode.MISSING_SCHEDULED_DATE));
                return;
            }
            LocalDate targetDate = parseScheduledDate(request.getScheduledDate(), observer);
            if (targetDate == null) return;

            int count = reservationBatchService.processOpenLimitReservations(targetDate);
            observer.onNext(ProcessOpenLimitReservationsResponse.newBuilder()
                    .setProcessedCount(count)
                    .build());
            observer.onCompleted();
        } catch (ReservationException e) {
            observer.onError(toGrpcException((ReservationErrorCode) e.errorCode()));
        }
    }

    @Override
    public void markReservationConverted(MarkReservationConvertedRequest request,
                                         StreamObserver<MarkReservationConvertedResponse> observer) {
        try {
            reservationBatchService.markConverted(
                    UUID.fromString(request.getReservationId()),
                    UUID.fromString(request.getConvertedOrderId()));
            var reservation = reservationRepository.findById(UUID.fromString(request.getReservationId()))
                    .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));
            observer.onNext(MarkReservationConvertedResponse.newBuilder()
                    .setReservation(toProto(reservation))
                    .build());
            observer.onCompleted();
        } catch (ReservationException e) {
            observer.onError(toGrpcException((ReservationErrorCode) e.errorCode()));
        } catch (IllegalArgumentException e) {
            observer.onError(toGrpcException(ReservationErrorCode.INVALID_ID_FORMAT));
        }
    }

    // ── 예외 매핑 (임시 — support/ 공통 계층 도입 시 제거) ──────────────
    private StatusRuntimeException toGrpcException(ReservationErrorCode errorCode) {
        Status status = switch (errorCode) {
            case INVALID_QUANTITY, INVALID_PRICE, LIMIT_RESERVATION_REQUIRES_PRICE,
                 NON_LIMIT_RESERVATION_MUST_NOT_HAVE_PRICE, TIMING_ORDER_KIND_MISMATCH,
                 INVALID_SCHEDULED_DATE, INVALID_SCHEDULED_DATE_FORMAT, MISSING_SCHEDULED_DATE,
                 INVALID_ID_FORMAT, INVALID_SIDE, INVALID_TIMING, INVALID_KIND, INVALID_STATUS ->
                    Status.INVALID_ARGUMENT;
            case RESERVATION_NOT_FOUND ->
                    Status.NOT_FOUND;
            case DUPLICATE_PENDING_RESERVATION, RESERVATION_NOT_RESERVED, RESERVATION_NOT_CONVERTING,
                 NOT_CONVERTIBLE, BATCH_DEADLINE_PASSED ->
                    Status.FAILED_PRECONDITION;
        };
        return status.withDescription(errorCode.message()).asRuntimeException();
    }

    private StatusRuntimeException toGrpcException(AccountErrorCode errorCode) {
        Status status = switch (errorCode) {
            case INVALID_LOCK_AMOUNT, INVALID_RELEASE_AMOUNT ->
                    Status.INVALID_ARGUMENT;
            case ACCOUNT_NOT_FOUND ->
                    Status.NOT_FOUND;
            case INSUFFICIENT_LOCKED_BALANCE, INSUFFICIENT_AVAILABLE_BALANCE,
                 INSUFFICIENT_CASH_BALANCE, ACCOUNT_INACTIVE ->
                    Status.FAILED_PRECONDITION;
        };
        return status.withDescription(errorCode.message()).asRuntimeException();
    }

    // ── 날짜 파싱 (DateTimeParseException → INVALID_ARGUMENT) ──────────
    private <T> LocalDate parseScheduledDate(String raw, StreamObserver<T> observer) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            observer.onError(toGrpcException(ReservationErrorCode.INVALID_SCHEDULED_DATE_FORMAT));
            return null;
        }
    }

    // ── actor / idempotency 컨텍스트 ────────────────────────────────────
    private UUID requireActor(String requestUserId) {
        IdempotencyContext context = IdempotencyContext.current();
        String actor = context == null ? null : context.actorId();
        if (actor == null || actor.isBlank()) {
            throw Status.UNAUTHENTICATED.withDescription("MISSING_ACTOR").asRuntimeException();
        }
        if (requestUserId != null && !requestUserId.isBlank() && !requestUserId.equals(actor)) {
            throw Status.PERMISSION_DENIED
                    .withDescription("user_id does not match authenticated actor")
                    .asRuntimeException();
        }
        return UUID.fromString(actor);
    }

    private String currentIdempotencyKey() {
        IdempotencyContext context = IdempotencyContext.current();
        return context == null ? null : context.idempotencyKey();
    }

    // ── 매핑 ──────────────────────────────────────────────────────────
    private Reservation toProto(ReservationEntity reservation) {
        Reservation.Builder builder = Reservation.newBuilder()
                .setId(reservation.getId().toString())
                .setUserId(reservation.getUserId().toString())
                .setSymbol(reservation.getSymbol())
                .setSide(toProtoSide(reservation.getSide()))
                .setTiming(toProtoTiming(reservation.getTiming()))
                .setKind(toProtoKind(reservation.getOrderKind()))
                .setQuantity(reservation.getQuantity())
                .setPrice(reservation.getPriceKrw() == null ? 0 : reservation.getPriceKrw())
                .setScheduledDate(reservation.getScheduledDate().toString())
                .setReservedAmount(reservation.getReservedAmountKrw())
                .setStatus(toProtoStatus(reservation.getStatus()));
        if (reservation.getParentReservationId() != null) {
            builder.setParentReservationId(reservation.getParentReservationId().toString());
        }
        if (reservation.getConvertedOrderId() != null) {
            builder.setConvertedOrderId(reservation.getConvertedOrderId().toString());
        }
        Instant createdAt = reservation.getCreatedAt() != null ? reservation.getCreatedAt() : Instant.now();
        builder.setCreatedAt(toTimestamp(createdAt));
        return builder.build();
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }

    private ReservationSideValue toSide(OrderSide side) {
        return switch (side) {
            case ORDER_SIDE_BUY -> ReservationSideValue.BUY;
            case ORDER_SIDE_SELL -> ReservationSideValue.SELL;
            default -> throw toGrpcException(ReservationErrorCode.INVALID_SIDE);
        };
    }

    private ReservationTimingValue toTiming(ReservationTiming timing) {
        return switch (timing) {
            case RESERVATION_TIMING_OPEN -> ReservationTimingValue.OPEN;
            case RESERVATION_TIMING_TODAY_CLOSE -> ReservationTimingValue.TODAY_CLOSE;
            case RESERVATION_TIMING_PREV_CLOSE -> ReservationTimingValue.PREV_CLOSE;
            default -> throw toGrpcException(ReservationErrorCode.INVALID_TIMING);
        };
    }

    private ReservationOrderKindValue toKind(OrderKind kind) {
        return switch (kind) {
            case ORDER_KIND_MARKET -> ReservationOrderKindValue.MARKET;
            case ORDER_KIND_LIMIT -> ReservationOrderKindValue.LIMIT;
            case ORDER_KIND_AFTER_HOURS_CLOSE -> ReservationOrderKindValue.AFTER_HOURS_CLOSE;
            default -> throw toGrpcException(ReservationErrorCode.INVALID_KIND);
        };
    }

    private ReservationStatusValue toStatus(ReservationStatus status) {
        return switch (status) {
            case RESERVATION_STATUS_RESERVED -> ReservationStatusValue.RESERVED;
            case RESERVATION_STATUS_EXECUTED -> ReservationStatusValue.EXECUTED;
            case RESERVATION_STATUS_CANCELLED -> ReservationStatusValue.CANCELLED;
            default -> throw toGrpcException(ReservationErrorCode.INVALID_STATUS);
        };
    }

    private OrderSide toProtoSide(ReservationSideValue side) {
        return side == ReservationSideValue.BUY ? OrderSide.ORDER_SIDE_BUY : OrderSide.ORDER_SIDE_SELL;
    }

    private ReservationTiming toProtoTiming(ReservationTimingValue timing) {
        return switch (timing) {
            case OPEN -> ReservationTiming.RESERVATION_TIMING_OPEN;
            case TODAY_CLOSE -> ReservationTiming.RESERVATION_TIMING_TODAY_CLOSE;
            case PREV_CLOSE -> ReservationTiming.RESERVATION_TIMING_PREV_CLOSE;
        };
    }

    private OrderKind toProtoKind(ReservationOrderKindValue kind) {
        return switch (kind) {
            case MARKET -> OrderKind.ORDER_KIND_MARKET;
            case LIMIT -> OrderKind.ORDER_KIND_LIMIT;
            case AFTER_HOURS_CLOSE -> OrderKind.ORDER_KIND_AFTER_HOURS_CLOSE;
        };
    }

    private ReservationStatus toProtoStatus(ReservationStatusValue status) {
        return switch (status) {
            case RESERVED, CONVERTING -> ReservationStatus.RESERVATION_STATUS_RESERVED;
            case EXECUTED -> ReservationStatus.RESERVATION_STATUS_EXECUTED;
            case CANCELLED, FAILED, EXPIRED -> ReservationStatus.RESERVATION_STATUS_CANCELLED;
        };
    }
}