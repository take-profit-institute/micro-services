package org.profit.candle.portfolio.holding.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.portfolio.holding.dto.HoldingResult;
import org.profit.candle.portfolio.holding.exception.HoldingErrorCode;
import org.profit.candle.portfolio.holding.exception.HoldingException;
import org.profit.candle.portfolio.holding.service.HoldingService;
import org.profit.candle.proto.portfolio.v1.GetHoldingRequest;
import org.profit.candle.proto.portfolio.v1.GetHoldingResponse;
import org.profit.candle.proto.portfolio.v1.ListHoldingsRequest;
import org.profit.candle.proto.portfolio.v1.ListHoldingsResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldingGrpcServiceTest {

    @Mock HoldingService holdingService;
    HoldingGrpcService service;

    private static final String USER_ID = "user-1";
    private static final String SYMBOL = "005930";

    @BeforeEach
    void setUp() {
        service = new HoldingGrpcService(holdingService);
    }

    private HoldingResult holdingResult() {
        return new HoldingResult(USER_ID, SYMBOL, "삼성전자", 10, 75_000, 750_000, 10_000, true, "반도체", "KOSPI");
    }

    // ─── listHoldings ───────────────────────────────────────────────────────

    @Test
    void listHoldings_happyPath_mapsHoldingsAndTotals() {
        when(holdingService.listHoldings(USER_ID, false)).thenReturn(List.of(holdingResult()));
        CapturingObserver<ListHoldingsResponse> observer = new CapturingObserver<>();

        service.listHoldings(
                ListHoldingsRequest.newBuilder().setUserId(USER_ID).build(), observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.error).isNull();
        assertThat(observer.value.getHoldingsList()).hasSize(1);
        assertThat(observer.value.getHoldings(0).getSymbol()).isEqualTo(SYMBOL);
        assertThat(observer.value.getHoldings(0).getName()).isEqualTo("삼성전자");
        assertThat(observer.value.getHoldings(0).getQuantity()).isEqualTo(10);
        assertThat(observer.value.getHoldings(0).getSector()).isEqualTo("반도체");
        assertThat(observer.value.getTotalBookValue()).isEqualTo(750_000);
        assertThat(observer.value.getTotalRealizedProfit()).isEqualTo(10_000);
    }

    @Test
    void listHoldings_includeInactiveFlagPassedThrough() {
        when(holdingService.listHoldings(USER_ID, true)).thenReturn(List.of());
        CapturingObserver<ListHoldingsResponse> observer = new CapturingObserver<>();

        service.listHoldings(
                ListHoldingsRequest.newBuilder().setUserId(USER_ID).setIncludeInactive(true).build(), observer);

        verify(holdingService).listHoldings(USER_ID, true);
        assertThat(observer.completed).isTrue();
    }

    @Test
    void listHoldings_emptyList_returnsZeroTotals() {
        when(holdingService.listHoldings(USER_ID, false)).thenReturn(List.of());
        CapturingObserver<ListHoldingsResponse> observer = new CapturingObserver<>();

        service.listHoldings(
                ListHoldingsRequest.newBuilder().setUserId(USER_ID).build(), observer);

        assertThat(observer.value.getHoldingsList()).isEmpty();
        assertThat(observer.value.getTotalBookValue()).isEqualTo(0);
    }

    // ─── getHolding ─────────────────────────────────────────────────────────

    @Test
    void getHolding_found_mapsToProto() {
        when(holdingService.getHolding(USER_ID, SYMBOL)).thenReturn(holdingResult());
        CapturingObserver<GetHoldingResponse> observer = new CapturingObserver<>();

        service.getHolding(
                GetHoldingRequest.newBuilder().setUserId(USER_ID).setSymbol(SYMBOL).build(), observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.value.getHolding().getSymbol()).isEqualTo(SYMBOL);
        assertThat(observer.value.getHolding().getQuantity()).isEqualTo(10);
        assertThat(observer.value.getHolding().getAveragePrice()).isEqualTo(75_000);
        assertThat(observer.value.getHolding().getActive()).isTrue();
    }

    @Test
    void getHolding_notFound_callsOnErrorWithNotFoundStatus() {
        when(holdingService.getHolding(USER_ID, SYMBOL))
                .thenThrow(new HoldingException(HoldingErrorCode.HOLDING_NOT_FOUND));
        CapturingObserver<GetHoldingResponse> observer = new CapturingObserver<>();

        service.getHolding(
                GetHoldingRequest.newBuilder().setUserId(USER_ID).setSymbol(SYMBOL).build(), observer);

        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) observer.error).getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
        assertThat(observer.completed).isFalse();
    }

    // ─── Helper ─────────────────────────────────────────────────────────────

    static class CapturingObserver<T> implements StreamObserver<T> {
        T value;
        Throwable error;
        boolean completed;

        @Override public void onNext(T v) { value = v; }
        @Override public void onError(Throwable t) { error = t; }
        @Override public void onCompleted() { completed = true; }
    }
}
