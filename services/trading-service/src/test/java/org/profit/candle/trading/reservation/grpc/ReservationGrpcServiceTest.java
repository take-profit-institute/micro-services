package org.profit.candle.trading.reservation.grpc;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.proto.trading.v1.*;
import org.profit.candle.trading.account.exception.AccountErrorCode;
import org.profit.candle.trading.account.exception.AccountException;
import org.profit.candle.trading.reservation.dto.ReservationCancelResult;
import org.profit.candle.trading.reservation.entity.ReservationEntity;
import org.profit.candle.trading.reservation.entity.ReservationOrderKindValue;
import org.profit.candle.trading.reservation.entity.ReservationSideValue;
import org.profit.candle.trading.reservation.entity.ReservationStatusValue;
import org.profit.candle.trading.reservation.entity.ReservationTimingValue;
import org.profit.candle.trading.reservation.event.ReservationIdempotencyOperations;
import org.profit.candle.trading.reservation.exception.ReservationErrorCode;
import org.profit.candle.trading.reservation.exception.ReservationException;
import org.profit.candle.trading.reservation.repository.ReservationRepository;
import org.profit.candle.trading.reservation.service.ReservationBatchService;
import org.profit.candle.trading.reservation.service.ReservationService;
import org.profit.candle.trading.support.idempotency.IdempotencyContext;
import org.profit.candle.trading.support.idempotency.IdempotencyExecutor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ReservationGrpcService 단위 테스트. IdempotencyExecutor는 mock으로 대체하되 command
 * Supplier를 그대로 실행시켜, 실제 알고리즘 없이도 서비스 결과가 응답까지 이어지는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ReservationGrpcServiceTest {

    @Mock private ReservationService reservationService;
    @Mock private ReservationBatchService reservationBatchService;
    @Mock private ReservationRepository reservationRepository;
    @Mock private IdempotencyExecutor idempotencyExecutor;
    @Mock private ReservationIdempotencyOperations idempotencyOperations;

    @Mock private StreamObserver<PlaceReservationResponse> placeObserver;
    @Mock private StreamObserver<CancelReservationResponse> cancelObserver;
    @Mock private StreamObserver<AmendReservationResponse> amendObserver;
    @Mock private StreamObserver<ListReservationsResponse> listObserver;
    @Mock private StreamObserver<ProcessOpenLimitReservationsResponse> processOpenLimitObserver;
    @Mock private StreamObserver<ListOpenLimitReservationsResponse> listOpenLimitObserver;
    @Mock private StreamObserver<ProcessSingleOpenLimitReservationResponse> processSingleObserver;
    @Mock private StreamObserver<ListStaleConvertingReservationsResponse> listStaleObserver;
    @Mock private StreamObserver<FailStaleConvertingReservationResponse> failStaleObserver;
    @Mock private StreamObserver<ListExpirableReservationsResponse> listExpirableObserver;
    @Mock private StreamObserver<ExpireReservationResponse> expireObserver;
    @Mock private StreamObserver<ProcessPrevCloseReservationsResponse> prevCloseObserver;
    @Mock private StreamObserver<ProcessTodayCloseReservationsResponse> todayCloseObserver;
    @Mock private StreamObserver<MarkReservationConvertedResponse> markConvertedObserver;

    private ReservationGrpcService grpcService;

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final LocalDate tomorrow = LocalDate.now().plusDays(1);

    @BeforeEach
    void setUp() {
        grpcService = new ReservationGrpcService(reservationService, reservationBatchService,
                reservationRepository, idempotencyExecutor, idempotencyOperations);
        lenient().when(idempotencyExecutor.execute(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> command = invocation.getArgument(3);
                    return command.get();
                });
    }

    private <T> T withActor(String actorId, Supplier<T> body) {
        Context context = Context.current().withValue(IdempotencyContext.CONTEXT_KEY,
                new IdempotencyContext(actorId, "ReservationService/Op", "idem-key"));
        Context previous = context.attach();
        try {
            return body.get();
        } finally {
            context.detach(previous);
        }
    }

    private void runWithActor(String actorId, Runnable body) {
        withActor(actorId, () -> {
            body.run();
            return null;
        });
    }

    private ReservationEntity openLimitReservation() {
        return ReservationEntity.reserve(userId, accountId, "005930", ReservationSideValue.BUY,
                ReservationTimingValue.OPEN, ReservationOrderKindValue.LIMIT, 10, 70_000L,
                tomorrow, 700_105L, "idem-r-" + UUID.randomUUID());
    }

    @Nested
    @DisplayName("listReservations")
    class ListReservations {

        @Test
        void shouldListAllWhenStatusUnspecified() {
            when(reservationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                    .thenReturn(List.of(openLimitReservation()));
            ListReservationsRequest request = ListReservationsRequest.newBuilder()
                    .setUserId(userId.toString()).build();

            runWithActor(userId.toString(), () -> grpcService.listReservations(request, listObserver));

            ArgumentCaptor<ListReservationsResponse> captor = ArgumentCaptor.forClass(ListReservationsResponse.class);
            verify(listObserver).onNext(captor.capture());
            assertThat(captor.getValue().getReservationsCount()).isEqualTo(1);
        }

        @Test
        void shouldFilterByStatusWhenProvided() {
            when(reservationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                    userId, ReservationStatusValue.EXECUTED)).thenReturn(List.of());
            ListReservationsRequest request = ListReservationsRequest.newBuilder()
                    .setUserId(userId.toString())
                    .setStatus(ReservationStatus.RESERVATION_STATUS_EXECUTED)
                    .build();

            runWithActor(userId.toString(), () -> grpcService.listReservations(request, listObserver));

            verify(reservationRepository).findByUserIdAndStatusOrderByCreatedAtDesc(
                    userId, ReservationStatusValue.EXECUTED);
        }

        @Test
        void shouldIncludeParentReservationIdWhenPresent() {
            // toProto()의 "parentReservationId != null" 분기 — 정정으로 생성된 예약만 값이 있다.
            ReservationEntity amended = openLimitReservation();
            amended.linkParent(UUID.randomUUID());
            when(reservationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                    .thenReturn(List.of(amended));
            ListReservationsRequest request = ListReservationsRequest.newBuilder()
                    .setUserId(userId.toString()).build();

            runWithActor(userId.toString(), () -> grpcService.listReservations(request, listObserver));

            ArgumentCaptor<ListReservationsResponse> captor = ArgumentCaptor.forClass(ListReservationsResponse.class);
            verify(listObserver).onNext(captor.capture());
            assertThat(captor.getValue().getReservations(0).getParentReservationId())
                    .isEqualTo(amended.getParentReservationId().toString());
        }

        @Test
        void shouldIncludeConvertedOrderIdWhenPresent() {
            // toProto()의 "convertedOrderId != null" 분기 — CONVERTING을 거쳐 전환 완료된 예약만 값이 있다.
            ReservationEntity converted = openLimitReservation();
            converted.startConverting();
            converted.markConverted(UUID.randomUUID());
            when(reservationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                    .thenReturn(List.of(converted));
            ListReservationsRequest request = ListReservationsRequest.newBuilder()
                    .setUserId(userId.toString()).build();

            runWithActor(userId.toString(), () -> grpcService.listReservations(request, listObserver));

            ArgumentCaptor<ListReservationsResponse> captor = ArgumentCaptor.forClass(ListReservationsResponse.class);
            verify(listObserver).onNext(captor.capture());
            assertThat(captor.getValue().getReservations(0).getConvertedOrderId())
                    .isEqualTo(converted.getConvertedOrderId().toString());
        }

        @Test
        void shouldMapPriceAsZeroWhenReservationHasNoPrice() {
            // toProto()의 "priceKrw == null ? 0 : ..." 분기 — 시장가/시간외종가 예약엔 가격이 없다.
            ReservationEntity marketReservation = ReservationEntity.reserve(userId, accountId, "005930",
                    ReservationSideValue.BUY, ReservationTimingValue.OPEN, ReservationOrderKindValue.MARKET,
                    10, null, tomorrow, 0L, "idem-market-" + UUID.randomUUID());
            when(reservationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                    .thenReturn(List.of(marketReservation));
            ListReservationsRequest request = ListReservationsRequest.newBuilder()
                    .setUserId(userId.toString()).build();

            runWithActor(userId.toString(), () -> grpcService.listReservations(request, listObserver));

            ArgumentCaptor<ListReservationsResponse> captor = ArgumentCaptor.forClass(ListReservationsResponse.class);
            verify(listObserver).onNext(captor.capture());
            assertThat(captor.getValue().getReservations(0).getPrice()).isZero();
        }
    }

    @Nested
    @DisplayName("placeReservation")
    class PlaceReservation {

        private PlaceReservationRequest.Builder validRequestBuilder() {
            return PlaceReservationRequest.newBuilder()
                    .setUserId(userId.toString()).setSymbol("005930")
                    .setSide(OrderSide.ORDER_SIDE_BUY)
                    .setTiming(ReservationTiming.RESERVATION_TIMING_OPEN)
                    .setKind(OrderKind.ORDER_KIND_LIMIT)
                    .setQuantity(10).setPrice(70_000)
                    .setScheduledDate(tomorrow.toString());
        }

        @Test
        void shouldReturnPlacedReservationOnSuccess() {
            ReservationEntity reservation = openLimitReservation();
            when(reservationService.placeReservation(eq(userId), any())).thenReturn(reservation);

            runWithActor(userId.toString(), () ->
                    grpcService.placeReservation(validRequestBuilder().build(), placeObserver));

            ArgumentCaptor<PlaceReservationResponse> captor = ArgumentCaptor.forClass(PlaceReservationResponse.class);
            verify(placeObserver).onNext(captor.capture());
            assertThat(captor.getValue().getReservation().getSymbol()).isEqualTo("005930");
        }

        @Test
        void shouldSkipDateParsingWhenScheduledDateIsBlank() {
            // PREV_CLOSE는 클라이언트가 scheduledDate를 안 보내도 되는 케이스 —
            // "!isBlank()" 분기의 false 경로(파싱 스킵, scheduledDate=null로 서비스 위임)를 검증.
            ReservationEntity reservation = ReservationEntity.reserve(userId, accountId, "005930",
                    ReservationSideValue.SELL, ReservationTimingValue.PREV_CLOSE,
                    ReservationOrderKindValue.AFTER_HOURS_CLOSE, 10, null, tomorrow, 0L, "idem-prevclose");
            when(reservationService.placeReservation(eq(userId), argThat(cmd -> cmd.scheduledDate() == null)))
                    .thenReturn(reservation);
            PlaceReservationRequest request = PlaceReservationRequest.newBuilder()
                    .setUserId(userId.toString()).setSymbol("005930")
                    .setSide(OrderSide.ORDER_SIDE_SELL)
                    .setTiming(ReservationTiming.RESERVATION_TIMING_PREV_CLOSE)
                    .setKind(OrderKind.ORDER_KIND_AFTER_HOURS_CLOSE)
                    .setQuantity(10)
                    // scheduledDate 미설정 → 기본값 "" (blank)
                    .build();

            runWithActor(userId.toString(), () -> grpcService.placeReservation(request, placeObserver));

            verify(reservationService).placeReservation(eq(userId), argThat(cmd -> cmd.scheduledDate() == null));
            verify(placeObserver).onNext(any());
        }

        @Test
        void shouldTreatZeroPriceAsNull() {
            ReservationEntity reservation = openLimitReservation();
            when(reservationService.placeReservation(eq(userId), argThat(cmd -> cmd.price() == null)))
                    .thenReturn(reservation);
            PlaceReservationRequest request = validRequestBuilder()
                    .setKind(OrderKind.ORDER_KIND_MARKET).setPrice(0).build();

            runWithActor(userId.toString(), () -> grpcService.placeReservation(request, placeObserver));

            verify(reservationService).placeReservation(eq(userId), argThat(cmd -> cmd.price() == null));
        }

        @Test
        void shouldRejectInvalidScheduledDateFormat() {
            PlaceReservationRequest request = validRequestBuilder().setScheduledDate("2026/07/07").build();

            runWithActor(userId.toString(), () -> grpcService.placeReservation(request, placeObserver));

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(placeObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
            verifyNoInteractions(reservationService);
        }

        @Test
        void shouldThrowInvalidSideWhenSideUnspecified() {
            PlaceReservationRequest request = validRequestBuilder()
                    .setSide(OrderSide.ORDER_SIDE_UNSPECIFIED).build();

            // toSide()가 던지는 StatusRuntimeException은 ReservationException이 아니라서
            // catch 블록에 안 걸린다 — observer.onError가 아니라 메서드 자체가 던진다.
            assertThatThrownBy(() -> runWithActor(userId.toString(), () ->
                    grpcService.placeReservation(request, placeObserver)))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
            verifyNoInteractions(placeObserver);
        }

        @Test
        void shouldThrowInvalidTimingWhenTimingUnspecified() {
            PlaceReservationRequest request = validRequestBuilder()
                    .setTiming(ReservationTiming.RESERVATION_TIMING_UNSPECIFIED).build();

            assertThatThrownBy(() -> runWithActor(userId.toString(), () ->
                    grpcService.placeReservation(request, placeObserver)))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
        }

        @Test
        void shouldThrowInvalidKindWhenKindUnspecified() {
            PlaceReservationRequest request = validRequestBuilder()
                    .setKind(OrderKind.ORDER_KIND_UNSPECIFIED).build();

            assertThatThrownBy(() -> runWithActor(userId.toString(), () ->
                    grpcService.placeReservation(request, placeObserver)))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
        }

        @Test
        void shouldMapReservationExceptionToGrpcStatus() {
            when(reservationService.placeReservation(eq(userId), any()))
                    .thenThrow(new ReservationException(ReservationErrorCode.DUPLICATE_PENDING_RESERVATION));

            runWithActor(userId.toString(), () ->
                    grpcService.placeReservation(validRequestBuilder().build(), placeObserver));

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(placeObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.FAILED_PRECONDITION);
        }

        @Test
        void shouldMapAccountExceptionToGrpcStatus() {
            when(reservationService.placeReservation(eq(userId), any()))
                    .thenThrow(new AccountException(AccountErrorCode.INSUFFICIENT_AVAILABLE_BALANCE));

            runWithActor(userId.toString(), () ->
                    grpcService.placeReservation(validRequestBuilder().build(), placeObserver));

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(placeObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.FAILED_PRECONDITION);
        }

        @Test
        void shouldMapDataIntegrityViolationToDuplicatePendingReservation() {
            when(reservationService.placeReservation(eq(userId), any()))
                    .thenThrow(new DataIntegrityViolationException("unique violation"));

            runWithActor(userId.toString(), () ->
                    grpcService.placeReservation(validRequestBuilder().build(), placeObserver));

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(placeObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.FAILED_PRECONDITION);
        }
    }

    @Nested
    @DisplayName("cancelReservation")
    class CancelReservation {

        @Test
        void shouldReturnCancelledReservationOnSuccess() {
            ReservationEntity reservation = openLimitReservation();
            reservation.markCancelled();
            when(reservationService.cancelReservation(eq(userId), any()))
                    .thenReturn(new ReservationCancelResult(reservation, 700_105L));
            CancelReservationRequest request = CancelReservationRequest.newBuilder()
                    .setUserId(userId.toString()).setReservationId(reservation.getId().toString()).build();

            runWithActor(userId.toString(), () -> grpcService.cancelReservation(request, cancelObserver));

            ArgumentCaptor<CancelReservationResponse> captor =
                    ArgumentCaptor.forClass(CancelReservationResponse.class);
            verify(cancelObserver).onNext(captor.capture());
            assertThat(captor.getValue().getReleasedAmount()).isEqualTo(700_105L);
        }

        @Test
        void shouldMapMalformedReservationIdToInvalidIdFormat() {
            CancelReservationRequest request = CancelReservationRequest.newBuilder()
                    .setUserId(userId.toString()).setReservationId("not-a-uuid").build();

            runWithActor(userId.toString(), () -> grpcService.cancelReservation(request, cancelObserver));

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(cancelObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
        }

        @Test
        void shouldMapReservationNotFoundToNotFound() {
            when(reservationService.cancelReservation(eq(userId), any()))
                    .thenThrow(new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));
            CancelReservationRequest request = CancelReservationRequest.newBuilder()
                    .setUserId(userId.toString()).setReservationId(UUID.randomUUID().toString()).build();

            runWithActor(userId.toString(), () -> grpcService.cancelReservation(request, cancelObserver));

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(cancelObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.NOT_FOUND);
        }

        @Test
        void shouldMapAccountExceptionToGrpcStatus() {
            when(reservationService.cancelReservation(eq(userId), any()))
                    .thenThrow(new AccountException(AccountErrorCode.ACCOUNT_NOT_FOUND));
            CancelReservationRequest request = CancelReservationRequest.newBuilder()
                    .setUserId(userId.toString()).setReservationId(UUID.randomUUID().toString()).build();

            runWithActor(userId.toString(), () -> grpcService.cancelReservation(request, cancelObserver));

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(cancelObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("amendReservation")
    class AmendReservation {

        @Test
        void shouldInheritOriginalFieldsWhenRequestFieldsAreUnspecified() {
            ReservationEntity amended = openLimitReservation();
            when(reservationService.amendReservation(eq(userId), argThat(cmd ->
                    cmd.timing() == null && cmd.kind() == null
                            && cmd.quantity() == null && cmd.price() == null
                            && cmd.scheduledDate() == null)))
                    .thenReturn(amended);
            AmendReservationRequest request = AmendReservationRequest.newBuilder()
                    .setUserId(userId.toString()).setReservationId(UUID.randomUUID().toString())
                    .build(); // timing/kind/quantity/price/scheduledDate 전부 미설정(0/UNSPECIFIED/blank)

            runWithActor(userId.toString(), () -> grpcService.amendReservation(request, amendObserver));

            verify(reservationService).amendReservation(eq(userId), argThat(cmd ->
                    cmd.timing() == null && cmd.kind() == null
                            && cmd.quantity() == null && cmd.price() == null));
            verify(amendObserver).onNext(any());
        }

        @Test
        void shouldPassActualValuesWhenRequestFieldsAreProvided() {
            // UNSPECIFIED/0/blank가 아닌 "진짜 새 값"을 보내는 분기 — 지금까지는 승계(null) 경로만 탔었음.
            ReservationEntity amended = openLimitReservation();
            when(reservationService.amendReservation(eq(userId), argThat(cmd ->
                    cmd.timing() == ReservationTimingValue.OPEN
                            && cmd.kind() == ReservationOrderKindValue.LIMIT
                            && cmd.quantity() == 5L && cmd.price() == 80_000L
                            && cmd.scheduledDate() != null)))
                    .thenReturn(amended);
            AmendReservationRequest request = AmendReservationRequest.newBuilder()
                    .setUserId(userId.toString()).setReservationId(UUID.randomUUID().toString())
                    .setTiming(ReservationTiming.RESERVATION_TIMING_OPEN)
                    .setKind(OrderKind.ORDER_KIND_LIMIT)
                    .setQuantity(5).setPrice(80_000)
                    .setScheduledDate(tomorrow.toString())
                    .build();

            runWithActor(userId.toString(), () -> grpcService.amendReservation(request, amendObserver));

            verify(reservationService).amendReservation(eq(userId), argThat(cmd ->
                    cmd.timing() == ReservationTimingValue.OPEN && cmd.quantity() == 5L));
        }

        @Test
        void shouldMapMalformedReservationIdToInvalidIdFormat() {
            AmendReservationRequest request = AmendReservationRequest.newBuilder()
                    .setUserId(userId.toString()).setReservationId("not-a-uuid").build();

            runWithActor(userId.toString(), () -> grpcService.amendReservation(request, amendObserver));

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(amendObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
        }

        @Test
        void shouldMapAccountExceptionToGrpcStatus() {
            when(reservationService.amendReservation(eq(userId), any()))
                    .thenThrow(new AccountException(AccountErrorCode.INSUFFICIENT_AVAILABLE_BALANCE));
            AmendReservationRequest request = AmendReservationRequest.newBuilder()
                    .setUserId(userId.toString()).setReservationId(UUID.randomUUID().toString()).build();

            runWithActor(userId.toString(), () -> grpcService.amendReservation(request, amendObserver));

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(amendObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.FAILED_PRECONDITION);
        }

        @Test
        void shouldMapDataIntegrityViolationToDuplicatePendingReservation() {
            when(reservationService.amendReservation(eq(userId), any()))
                    .thenThrow(new DataIntegrityViolationException("unique violation"));
            AmendReservationRequest request = AmendReservationRequest.newBuilder()
                    .setUserId(userId.toString()).setReservationId(UUID.randomUUID().toString()).build();

            runWithActor(userId.toString(), () -> grpcService.amendReservation(request, amendObserver));

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(amendObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.FAILED_PRECONDITION);
        }

        @Test
        void shouldRejectInvalidScheduledDateFormat() {
            AmendReservationRequest request = AmendReservationRequest.newBuilder()
                    .setUserId(userId.toString()).setReservationId(UUID.randomUUID().toString())
                    .setScheduledDate("not-a-date").build();

            runWithActor(userId.toString(), () -> grpcService.amendReservation(request, amendObserver));

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(amendObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
            verifyNoInteractions(reservationService);
        }
    }

    @Nested
    @DisplayName("배치 전용 RPC — 일별")
    class BatchDaily {

        @Test
        void processOpenLimitReservations_shouldRejectBlankScheduledDate() {
            ProcessOpenLimitReservationsRequest request =
                    ProcessOpenLimitReservationsRequest.newBuilder().build();

            grpcService.processOpenLimitReservations(request, processOpenLimitObserver);

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(processOpenLimitObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
            verifyNoInteractions(reservationBatchService);
        }

        @Test
        void processOpenLimitReservations_shouldRejectMalformedScheduledDateFormat() {
            ProcessOpenLimitReservationsRequest request = ProcessOpenLimitReservationsRequest.newBuilder()
                    .setScheduledDate("2026/07/07").build();

            grpcService.processOpenLimitReservations(request, processOpenLimitObserver);

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(processOpenLimitObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
            verifyNoInteractions(reservationBatchService);
        }

        @Test
        void processOpenLimitReservations_shouldMapServiceReservationException() {
            when(reservationBatchService.processOpenLimitReservations(tomorrow))
                    .thenThrow(new ReservationException(ReservationErrorCode.BATCH_DEADLINE_PASSED));
            ProcessOpenLimitReservationsRequest request = ProcessOpenLimitReservationsRequest.newBuilder()
                    .setScheduledDate(tomorrow.toString()).build();

            grpcService.processOpenLimitReservations(request, processOpenLimitObserver);

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(processOpenLimitObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.FAILED_PRECONDITION);
        }

        @Test
        void processOpenLimitReservations_shouldReturnProcessedCountOnSuccess() {
            when(reservationBatchService.processOpenLimitReservations(tomorrow)).thenReturn(5);
            ProcessOpenLimitReservationsRequest request = ProcessOpenLimitReservationsRequest.newBuilder()
                    .setScheduledDate(tomorrow.toString()).build();

            grpcService.processOpenLimitReservations(request, processOpenLimitObserver);

            ArgumentCaptor<ProcessOpenLimitReservationsResponse> captor =
                    ArgumentCaptor.forClass(ProcessOpenLimitReservationsResponse.class);
            verify(processOpenLimitObserver).onNext(captor.capture());
            assertThat(captor.getValue().getProcessedCount()).isEqualTo(5);
        }

        @Test
        void processPrevCloseReservations_shouldRejectBlankScheduledDate() {
            ProcessPrevCloseReservationsRequest request =
                    ProcessPrevCloseReservationsRequest.newBuilder().build();

            grpcService.processPrevCloseReservations(request, prevCloseObserver);

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(prevCloseObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
            verifyNoInteractions(reservationBatchService);
        }

        @Test
        void processPrevCloseReservations_shouldReturnProcessedCount() {
            when(reservationBatchService.processPrevCloseReservations(tomorrow)).thenReturn(2);
            ProcessPrevCloseReservationsRequest request = ProcessPrevCloseReservationsRequest.newBuilder()
                    .setScheduledDate(tomorrow.toString()).build();

            grpcService.processPrevCloseReservations(request, prevCloseObserver);

            ArgumentCaptor<ProcessPrevCloseReservationsResponse> captor =
                    ArgumentCaptor.forClass(ProcessPrevCloseReservationsResponse.class);
            verify(prevCloseObserver).onNext(captor.capture());
            assertThat(captor.getValue().getProcessedCount()).isEqualTo(2);
        }

        @Test
        void processTodayCloseReservations_shouldRejectBlankScheduledDate() {
            ProcessTodayCloseReservationsRequest request =
                    ProcessTodayCloseReservationsRequest.newBuilder().build();

            grpcService.processTodayCloseReservations(request, todayCloseObserver);

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(todayCloseObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
            verifyNoInteractions(reservationBatchService);
        }

        @Test
        void processTodayCloseReservations_shouldReturnProcessedCount() {
            when(reservationBatchService.processTodayCloseReservations(tomorrow)).thenReturn(1);
            ProcessTodayCloseReservationsRequest request = ProcessTodayCloseReservationsRequest.newBuilder()
                    .setScheduledDate(tomorrow.toString()).build();

            grpcService.processTodayCloseReservations(request, todayCloseObserver);

            verify(todayCloseObserver).onNext(any());
        }

        @Test
        void markReservationConverted_shouldReturnUpdatedReservationOnSuccess() {
            ReservationEntity reservation = openLimitReservation();
            UUID orderId = UUID.randomUUID();
            when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
            MarkReservationConvertedRequest request = MarkReservationConvertedRequest.newBuilder()
                    .setReservationId(reservation.getId().toString())
                    .setConvertedOrderId(orderId.toString()).build();

            grpcService.markReservationConverted(request, markConvertedObserver);

            verify(reservationBatchService).markConverted(reservation.getId(), orderId);
            verify(markConvertedObserver).onNext(any());
        }

        @Test
        void markReservationConverted_shouldMapMalformedReservationIdToInvalidArgument() {
            MarkReservationConvertedRequest request = MarkReservationConvertedRequest.newBuilder()
                    .setReservationId("bad-id").setConvertedOrderId(UUID.randomUUID().toString()).build();

            grpcService.markReservationConverted(request, markConvertedObserver);

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(markConvertedObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
        }

        @Test
        void markReservationConverted_shouldMapMalformedConvertedOrderIdToInvalidArgument() {
            MarkReservationConvertedRequest request = MarkReservationConvertedRequest.newBuilder()
                    .setReservationId(UUID.randomUUID().toString()).setConvertedOrderId("bad-order-id").build();

            grpcService.markReservationConverted(request, markConvertedObserver);

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(markConvertedObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
            verifyNoInteractions(reservationBatchService);
        }

        @Test
        void markReservationConverted_shouldMapNotFoundWhenReservationMissingAfterMarkConverted() {
            // markConverted 자체는 성공했는데(다른 트랜잭션이 이미 삭제했다거나 하는 극단 상황),
            // 재조회 시점에 findById가 비어있는 경우 — RESERVATION_NOT_FOUND로 매핑돼야 한다.
            UUID reservationId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());
            MarkReservationConvertedRequest request = MarkReservationConvertedRequest.newBuilder()
                    .setReservationId(reservationId.toString())
                    .setConvertedOrderId(orderId.toString()).build();

            grpcService.markReservationConverted(request, markConvertedObserver);

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(markConvertedObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("배치 전용 RPC — 건별")
    class BatchPerRecord {

        @Test
        void listOpenLimitReservations_shouldReturnIdsAsStrings() {
            UUID id = UUID.randomUUID();
            when(reservationBatchService.listOpenLimitReservationIds(tomorrow)).thenReturn(List.of(id));
            ListOpenLimitReservationsRequest request = ListOpenLimitReservationsRequest.newBuilder()
                    .setScheduledDate(tomorrow.toString()).build();

            grpcService.listOpenLimitReservations(request, listOpenLimitObserver);

            ArgumentCaptor<ListOpenLimitReservationsResponse> captor =
                    ArgumentCaptor.forClass(ListOpenLimitReservationsResponse.class);
            verify(listOpenLimitObserver).onNext(captor.capture());
            assertThat(captor.getValue().getReservationIds(0)).isEqualTo(id.toString());
        }

        @Test
        void listOpenLimitReservations_shouldRejectBlankScheduledDate() {
            ListOpenLimitReservationsRequest request = ListOpenLimitReservationsRequest.newBuilder().build();

            grpcService.listOpenLimitReservations(request, listOpenLimitObserver);

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(listOpenLimitObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
            verifyNoInteractions(reservationBatchService);
        }

        @Test
        void processSingleOpenLimitReservation_shouldReturnProcessedFlag() {
            UUID id = UUID.randomUUID();
            when(reservationBatchService.processSingleOpenLimitReservation(id)).thenReturn(true);
            ProcessSingleOpenLimitReservationRequest request =
                    ProcessSingleOpenLimitReservationRequest.newBuilder().setReservationId(id.toString()).build();

            grpcService.processSingleOpenLimitReservation(request, processSingleObserver);

            ArgumentCaptor<ProcessSingleOpenLimitReservationResponse> captor =
                    ArgumentCaptor.forClass(ProcessSingleOpenLimitReservationResponse.class);
            verify(processSingleObserver).onNext(captor.capture());
            assertThat(captor.getValue().getProcessed()).isTrue();
        }

        @Test
        void processSingleOpenLimitReservation_shouldMapMalformedIdToInvalidArgument() {
            ProcessSingleOpenLimitReservationRequest request =
                    ProcessSingleOpenLimitReservationRequest.newBuilder().setReservationId("bad").build();

            grpcService.processSingleOpenLimitReservation(request, processSingleObserver);

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(processSingleObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
        }

        @Test
        void processSingleOpenLimitReservation_shouldMapServiceReservationException() {
            UUID id = UUID.randomUUID();
            when(reservationBatchService.processSingleOpenLimitReservation(id))
                    .thenThrow(new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));
            ProcessSingleOpenLimitReservationRequest request =
                    ProcessSingleOpenLimitReservationRequest.newBuilder().setReservationId(id.toString()).build();

            grpcService.processSingleOpenLimitReservation(request, processSingleObserver);

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(processSingleObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.NOT_FOUND);
        }

        @Test
        void listStaleConvertingReservations_shouldRejectBlankScheduledDate() {
            ListStaleConvertingReservationsRequest request =
                    ListStaleConvertingReservationsRequest.newBuilder().build();

            grpcService.listStaleConvertingReservations(request, listStaleObserver);

            verify(listStaleObserver).onError(any());
            verifyNoInteractions(reservationBatchService);
        }

        @Test
        void listStaleConvertingReservations_shouldReturnIds() {
            UUID id = UUID.randomUUID();
            when(reservationBatchService.listStaleConvertingReservationIds(tomorrow)).thenReturn(List.of(id));
            ListStaleConvertingReservationsRequest request = ListStaleConvertingReservationsRequest.newBuilder()
                    .setScheduledDate(tomorrow.toString()).build();

            grpcService.listStaleConvertingReservations(request, listStaleObserver);

            verify(listStaleObserver).onNext(any());
        }

        @Test
        void failStaleConvertingReservation_shouldReturnFailedFlag() {
            UUID id = UUID.randomUUID();
            when(reservationBatchService.failStaleConvertingReservation(id)).thenReturn(true);
            FailStaleConvertingReservationRequest request = FailStaleConvertingReservationRequest.newBuilder()
                    .setReservationId(id.toString()).build();

            grpcService.failStaleConvertingReservation(request, failStaleObserver);

            ArgumentCaptor<FailStaleConvertingReservationResponse> captor =
                    ArgumentCaptor.forClass(FailStaleConvertingReservationResponse.class);
            verify(failStaleObserver).onNext(captor.capture());
            assertThat(captor.getValue().getFailed()).isTrue();
        }

        @Test
        void failStaleConvertingReservation_shouldMapServiceReservationException() {
            UUID id = UUID.randomUUID();
            when(reservationBatchService.failStaleConvertingReservation(id))
                    .thenThrow(new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));
            FailStaleConvertingReservationRequest request = FailStaleConvertingReservationRequest.newBuilder()
                    .setReservationId(id.toString()).build();

            grpcService.failStaleConvertingReservation(request, failStaleObserver);

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(failStaleObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.NOT_FOUND);
        }

        @Test
        void failStaleConvertingReservation_shouldMapMalformedIdToInvalidArgument() {
            FailStaleConvertingReservationRequest request = FailStaleConvertingReservationRequest.newBuilder()
                    .setReservationId("bad").build();

            grpcService.failStaleConvertingReservation(request, failStaleObserver);

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(failStaleObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
        }

        @Test
        void listExpirableReservations_shouldReturnIds() {
            UUID id = UUID.randomUUID();
            when(reservationBatchService.listExpirableReservationIds(tomorrow)).thenReturn(List.of(id));
            ListExpirableReservationsRequest request = ListExpirableReservationsRequest.newBuilder()
                    .setScheduledDate(tomorrow.toString()).build();

            grpcService.listExpirableReservations(request, listExpirableObserver);

            verify(listExpirableObserver).onNext(any());
        }

        @Test
        void listExpirableReservations_shouldRejectBlankScheduledDate() {
            ListExpirableReservationsRequest request = ListExpirableReservationsRequest.newBuilder().build();

            grpcService.listExpirableReservations(request, listExpirableObserver);

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(listExpirableObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
            verifyNoInteractions(reservationBatchService);
        }

        @Test
        void expireReservation_shouldReturnExpiredFlag() {
            UUID id = UUID.randomUUID();
            when(reservationBatchService.expireReservation(id)).thenReturn(false);
            ExpireReservationRequest request = ExpireReservationRequest.newBuilder()
                    .setReservationId(id.toString()).build();

            grpcService.expireReservation(request, expireObserver);

            ArgumentCaptor<ExpireReservationResponse> captor =
                    ArgumentCaptor.forClass(ExpireReservationResponse.class);
            verify(expireObserver).onNext(captor.capture());
            assertThat(captor.getValue().getExpired()).isFalse();
        }

        @Test
        void expireReservation_shouldMapMalformedIdToInvalidArgument() {
            ExpireReservationRequest request = ExpireReservationRequest.newBuilder()
                    .setReservationId("bad").build();

            grpcService.expireReservation(request, expireObserver);

            verify(expireObserver).onError(any());
        }

        @Test
        void expireReservation_shouldMapServiceReservationException() {
            UUID id = UUID.randomUUID();
            when(reservationBatchService.expireReservation(id))
                    .thenThrow(new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));
            ExpireReservationRequest request = ExpireReservationRequest.newBuilder()
                    .setReservationId(id.toString()).build();

            grpcService.expireReservation(request, expireObserver);

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(expireObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.NOT_FOUND);
        }
    }

    /** ReservationErrorCode → Status 매핑 switch 전체 분기 강제 실행. */
    @ParameterizedTest(name = "{0}")
    @EnumSource(ReservationErrorCode.class)
    @DisplayName("ReservationErrorCode 전체 값이 예외 없이 gRPC Status로 매핑된다")
    void shouldMapEveryReservationErrorCodeToSomeGrpcStatus(ReservationErrorCode errorCode) {
        when(reservationService.cancelReservation(eq(userId), any()))
                .thenThrow(new ReservationException(errorCode));
        CancelReservationRequest request = CancelReservationRequest.newBuilder()
                .setUserId(userId.toString()).setReservationId(UUID.randomUUID().toString()).build();

        runWithActor(userId.toString(), () -> grpcService.cancelReservation(request, cancelObserver));

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(cancelObserver).onError(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(StatusRuntimeException.class);
    }

    /** AccountErrorCode 매핑도(ReservationGrpcService에 중복 존재) 전체 분기 실행. */
    @ParameterizedTest(name = "{0}")
    @EnumSource(AccountErrorCode.class)
    @DisplayName("AccountErrorCode 전체 값이 예외 없이 gRPC Status로 매핑된다 (ReservationGrpcService 중복 매핑)")
    void shouldMapEveryAccountErrorCodeToSomeGrpcStatus(AccountErrorCode errorCode) {
        when(reservationService.cancelReservation(eq(userId), any()))
                .thenThrow(new AccountException(errorCode));
        CancelReservationRequest request = CancelReservationRequest.newBuilder()
                .setUserId(userId.toString()).setReservationId(UUID.randomUUID().toString()).build();

        runWithActor(userId.toString(), () -> grpcService.cancelReservation(request, cancelObserver));

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(cancelObserver).onError(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(StatusRuntimeException.class);
    }

    @Nested
    @DisplayName("requireActor 공통 검증")
    class RequireActor {

        @Test
        void shouldThrowUnauthenticatedWhenNoContextAttached() {
            ListReservationsRequest request = ListReservationsRequest.newBuilder()
                    .setUserId(userId.toString()).build();

            assertThatThrownBy(() -> grpcService.listReservations(request, listObserver))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAUTHENTICATED);
        }

        @Test
        void shouldThrowPermissionDeniedWhenRequestUserIdMismatchesActor() {
            String otherUserId = UUID.randomUUID().toString();
            ListReservationsRequest request = ListReservationsRequest.newBuilder()
                    .setUserId(otherUserId).build();

            assertThatThrownBy(() -> withActor(userId.toString(), () -> {
                grpcService.listReservations(request, listObserver);
                return null;
            }))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.PERMISSION_DENIED);
        }
    }
}