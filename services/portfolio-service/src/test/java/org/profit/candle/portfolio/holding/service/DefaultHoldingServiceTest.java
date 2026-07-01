package org.profit.candle.portfolio.holding.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.portfolio.holding.dto.HoldingResult;
import org.profit.candle.portfolio.holding.entity.HoldingEntity;
import org.profit.candle.portfolio.holding.exception.HoldingErrorCode;
import org.profit.candle.portfolio.holding.repository.HoldingReader;
import org.profit.candle.portfolio.holding.repository.HoldingWriter;
import org.profit.candle.portfolio.holding.trade.entity.RealizedTradeEntity;
import org.profit.candle.portfolio.holding.trade.repository.RealizedTradeWriter;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultHoldingServiceTest {

    @Mock HoldingReader holdingReader;
    @Mock HoldingWriter holdingWriter;
    @Mock RealizedTradeWriter realizedTradeWriter;
    @InjectMocks DefaultHoldingService service;

    private static final String USER_ID = "user-1";
    private static final String SYMBOL = "005930";

    private HoldingEntity activeHolding() {
        HoldingEntity h = new HoldingEntity(USER_ID, SYMBOL, "삼성전자", "반도체", "KOSPI");
        h.applyBuy(10, 75_000);
        return h;
    }

    // ─── listHoldings ───────────────────────────────────────────────────────

    @Test
    void listHoldings_activeOnly_delegatesToFindActive() {
        when(holdingReader.findActiveByUserId(USER_ID)).thenReturn(List.of(activeHolding()));

        List<HoldingResult> results = service.listHoldings(USER_ID, false);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).symbol()).isEqualTo(SYMBOL);
        verify(holdingReader).findActiveByUserId(USER_ID);
        verify(holdingReader, never()).findByUserId(any());
    }

    @Test
    void listHoldings_includeInactive_delegatesToFindAll() {
        when(holdingReader.findByUserId(USER_ID)).thenReturn(List.of());

        service.listHoldings(USER_ID, true);

        verify(holdingReader).findByUserId(USER_ID);
        verify(holdingReader, never()).findActiveByUserId(any());
    }

    @Test
    void listHoldings_mapsEntityToResult() {
        HoldingEntity entity = activeHolding();
        when(holdingReader.findActiveByUserId(USER_ID)).thenReturn(List.of(entity));

        List<HoldingResult> results = service.listHoldings(USER_ID, false);

        HoldingResult r = results.get(0);
        assertThat(r.userId()).isEqualTo(USER_ID);
        assertThat(r.quantity()).isEqualTo(10);
        assertThat(r.averagePrice()).isEqualTo(75_000);
        assertThat(r.bookValue()).isEqualTo(750_000);
        assertThat(r.sector()).isEqualTo("반도체");
    }

    // ─── getHolding ─────────────────────────────────────────────────────────

    @Test
    void getHolding_found_returnsResult() {
        when(holdingReader.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(Optional.of(activeHolding()));

        HoldingResult result = service.getHolding(USER_ID, SYMBOL);

        assertThat(result.symbol()).isEqualTo(SYMBOL);
        assertThat(result.quantity()).isEqualTo(10);
    }

    @Test
    void getHolding_notFound_throwsHoldingNotFound() {
        when(holdingReader.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHolding(USER_ID, SYMBOL))
                .isInstanceOf(CandleException.class)
                .satisfies(ex -> assertThat(((CandleException) ex).errorCode())
                        .isEqualTo(HoldingErrorCode.HOLDING_NOT_FOUND));
    }

    @Test
    void getHolding_inactiveHolding_throwsHoldingNotFound() {
        HoldingEntity inactive = new HoldingEntity(USER_ID, SYMBOL, "", "", ""); // quantity=0, active=false
        when(holdingReader.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.getHolding(USER_ID, SYMBOL))
                .isInstanceOf(CandleException.class)
                .satisfies(ex -> assertThat(((CandleException) ex).errorCode())
                        .isEqualTo(HoldingErrorCode.HOLDING_NOT_FOUND));
    }

    // ─── applyBuyFill ───────────────────────────────────────────────────────

    @Test
    void applyBuyFill_existingHolding_appliesBuyAndSaves() {
        HoldingEntity existing = activeHolding(); // qty=10, avg=75000
        when(holdingReader.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(Optional.of(existing));
        when(holdingWriter.save(existing)).thenReturn(existing);

        service.applyBuyFill(USER_ID, SYMBOL, 5, 90_000);

        assertThat(existing.quantity()).isEqualTo(15);
        verify(holdingWriter).save(existing);
    }

    @Test
    void applyBuyFill_newHolding_createsEntityAndSaves() {
        when(holdingReader.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(Optional.empty());
        when(holdingWriter.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyBuyFill(USER_ID, SYMBOL, 10, 75_000);

        verify(holdingWriter).save(any(HoldingEntity.class));
    }

    // ─── applySellFill ──────────────────────────────────────────────────────

    @Test
    void applySellFill_existingHolding_appliesSellAndSaves() {
        HoldingEntity existing = activeHolding(); // qty=10
        when(holdingReader.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(Optional.of(existing));
        when(holdingWriter.save(existing)).thenReturn(existing);

        service.applySellFill(USER_ID, SYMBOL, 5, 80_000);

        assertThat(existing.quantity()).isEqualTo(5);
        assertThat(existing.realizedProfit()).isEqualTo(25_000);
        verify(holdingWriter).save(existing);
        verify(realizedTradeWriter).save(any(RealizedTradeEntity.class));
    }

    @Test
    void applySellFill_holdingNotFound_throwsHoldingNotFound() {
        when(holdingReader.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applySellFill(USER_ID, SYMBOL, 5, 80_000))
                .isInstanceOf(CandleException.class)
                .satisfies(ex -> assertThat(((CandleException) ex).errorCode())
                        .isEqualTo(HoldingErrorCode.HOLDING_NOT_FOUND));

        verify(holdingWriter, never()).save(any());
    }
}
