package org.profit.candle.portfolio.holding.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.portfolio.holding.dto.HoldingResult;
import org.profit.candle.portfolio.holding.entity.HoldingEntity;
import org.profit.candle.portfolio.holding.entity.SellOutcome;
import org.profit.candle.portfolio.holding.exception.HoldingErrorCode;
import org.profit.candle.portfolio.holding.exception.HoldingException;
import org.profit.candle.portfolio.holding.repository.HoldingReader;
import org.profit.candle.portfolio.holding.repository.HoldingWriter;
import org.profit.candle.portfolio.holding.trade.entity.RealizedTradeEntity;
import org.profit.candle.portfolio.holding.trade.repository.RealizedTradeWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultHoldingService implements HoldingService {

    private final HoldingReader holdingReader;
    private final HoldingWriter holdingWriter;
    private final RealizedTradeWriter realizedTradeWriter;

    @Override
    @Transactional(readOnly = true)
    public List<HoldingResult> listHoldings(String userId, boolean includeInactive) {
        List<HoldingEntity> holdings = includeInactive
                ? holdingReader.findByUserId(userId)
                : holdingReader.findActiveByUserId(userId);
        return holdings.stream().map(HoldingResult::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HoldingResult getHolding(String userId, String symbol) {
        return holdingReader.findByUserIdAndSymbol(userId, symbol)
                .filter(HoldingEntity::active)
                .map(HoldingResult::from)
                .orElseThrow(() -> new HoldingException(HoldingErrorCode.HOLDING_NOT_FOUND));
    }

    @Override
    @Transactional
    public void applyBuyFill(String userId, String symbol, long quantity, long executedPrice) {
        HoldingEntity holding = holdingReader.findByUserIdAndSymbol(userId, symbol)
                .orElseGet(() -> new HoldingEntity(userId, symbol, "", "", ""));
        holding.applyBuy(quantity, executedPrice);
        holdingWriter.save(holding);
    }

    @Override
    @Transactional
    public void applySellFill(String userId, String symbol, long quantity, long executedPrice) {
        HoldingEntity holding = holdingReader.findByUserIdAndSymbol(userId, symbol)
                .orElseThrow(() -> new HoldingException(HoldingErrorCode.HOLDING_NOT_FOUND));
        SellOutcome outcome = holding.applySell(quantity, executedPrice);
        holdingWriter.save(holding);
        realizedTradeWriter.save(new RealizedTradeEntity(userId, symbol, outcome));
    }
}
