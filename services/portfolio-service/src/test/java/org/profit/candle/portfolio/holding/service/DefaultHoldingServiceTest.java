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
import org.profit.candle.portfolio.holding.stock.StockMetadata;
import org.profit.candle.portfolio.holding.stock.StockMetadataClient;
import org.profit.candle.portfolio.holding.trade.entity.RealizedTradeEntity;
import org.profit.candle.portfolio.holding.trade.repository.RealizedTradeWriter;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultHoldingServiceTest {

    @Mock HoldingReader holdingReader;
    @Mock HoldingWriter holdingWriter;
    @Mock RealizedTradeWriter realizedTradeWriter;
    @Mock StockMetadataClient stockMetadataClient;
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

    // ─── listActiveHolders (배치 EOD cursor) ────────────────────────────────

    private HoldingEntity holding(String userId, String symbol, long qty, long price) {
        HoldingEntity h = new HoldingEntity(userId, symbol, "name", "sector", "KOSPI");
        h.applyBuy(qty, price);
        return h;
    }

    @Test
    void listActiveHolders_groupsPositionsByUser_userIdAscNoNext() {
        when(holdingReader.findActiveUserIdsAfter(isNull(), eq(101)))
                .thenReturn(List.of("user-1", "user-2"));
        when(holdingReader.findActiveHoldingsByUserIds(List.of("user-1", "user-2")))
                .thenReturn(List.of(
                        holding("user-1", "000660", 3, 100_000),
                        holding("user-1", "005930", 10, 75_000),
                        holding("user-2", "035720", 5, 50_000)));

        var result = service.listActiveHolders(0, "");

        assertThat(result.holders()).hasSize(2);
        assertThat(result.holders().get(0).userId()).isEqualTo("user-1");
        assertThat(result.holders().get(0).positions()).extracting("symbol")
                .containsExactly("000660", "005930");
        assertThat(result.holders().get(0).positions().get(1).quantity()).isEqualTo(10);
        assertThat(result.holders().get(0).positions().get(1).averagePrice()).isEqualTo(75_000);
        assertThat(result.holders().get(1).userId()).isEqualTo("user-2");
        assertThat(result.holders().get(1).positions()).hasSize(1);
        assertThat(result.nextPageToken()).isEmpty();
    }

    @Test
    void listActiveHolders_hasNext_trimsToPageSizeAndReturnsLastUserIdToken() {
        // pageSize=2 → pageSize+1=3 조회, 3개 오면 hasNext, 페이지는 앞 2명, 토큰은 2번째 user_id
        when(holdingReader.findActiveUserIdsAfter(isNull(), eq(3)))
                .thenReturn(List.of("user-1", "user-2", "user-3"));
        when(holdingReader.findActiveHoldingsByUserIds(List.of("user-1", "user-2")))
                .thenReturn(List.of(
                        holding("user-1", "005930", 1, 70_000),
                        holding("user-2", "035720", 2, 50_000)));

        var result = service.listActiveHolders(2, "");

        assertThat(result.holders()).extracting("userId").containsExactly("user-1", "user-2");
        assertThat(result.nextPageToken()).isEqualTo("user-2");
    }

    @Test
    void listActiveHolders_usesPageTokenAsLastUserId() {
        when(holdingReader.findActiveUserIdsAfter(eq("user-100"), eq(3)))
                .thenReturn(List.of("user-101"));
        when(holdingReader.findActiveHoldingsByUserIds(List.of("user-101")))
                .thenReturn(List.of(holding("user-101", "005930", 1, 70_000)));

        var result = service.listActiveHolders(2, "user-100");

        assertThat(result.holders()).extracting("userId").containsExactly("user-101");
        assertThat(result.nextPageToken()).isEmpty();
    }

    @Test
    void listActiveHolders_noActiveUsers_returnsEmptyWithoutSecondQuery() {
        when(holdingReader.findActiveUserIdsAfter(isNull(), eq(101))).thenReturn(List.of());

        var result = service.listActiveHolders(0, null);

        assertThat(result.holders()).isEmpty();
        assertThat(result.nextPageToken()).isEmpty();
        verify(holdingReader, never()).findActiveHoldingsByUserIds(anyList());
    }

    @Test
    void listActiveHolders_rejectsPageSizeOverMax() {
        assertThatThrownBy(() -> service.listActiveHolders(501, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page_size must be between 1 and 500");
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
        verify(stockMetadataClient, never()).getMetadata(any(String.class));
    }

    @Test
    void applyBuyFill_newHolding_enrichesMetadataAndSaves() {
        when(holdingReader.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(Optional.empty());
        when(holdingWriter.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockMetadataClient.getMetadata(SYMBOL))
                .thenReturn(new StockMetadata("삼성전자", "반도체", "KOSPI"));

        service.applyBuyFill(USER_ID, SYMBOL, 10, 75_000);

        verify(holdingWriter).save(argThat(holding ->
                holding.name().equals("삼성전자")
                        && holding.sector().equals("반도체")
                        && holding.market().equals("KOSPI")
                        && holding.quantity() == 10));
    }

    @Test
    void applyBuyFill_existingHoldingWithMissingMetadata_enrichesBeforeSave() {
        HoldingEntity existing = new HoldingEntity(USER_ID, SYMBOL, "", "", "");
        when(holdingReader.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(Optional.of(existing));
        when(holdingWriter.save(existing)).thenReturn(existing);
        when(stockMetadataClient.getMetadata(SYMBOL))
                .thenReturn(new StockMetadata("삼성전자", "반도체", "KOSPI"));

        service.applyBuyFill(USER_ID, SYMBOL, 10, 75_000);

        assertThat(existing.name()).isEqualTo("삼성전자");
        assertThat(existing.sector()).isEqualTo("반도체");
        assertThat(existing.market()).isEqualTo("KOSPI");
        verify(holdingWriter).save(existing);
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
