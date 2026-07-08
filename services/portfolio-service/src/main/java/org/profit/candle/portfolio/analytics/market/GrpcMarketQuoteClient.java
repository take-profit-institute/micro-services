package org.profit.candle.portfolio.analytics.market;

import io.grpc.Deadline;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.proto.market.v1.BatchQuotesRequest;
import org.profit.candle.proto.market.v1.MarketServiceGrpc;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Portfolio analytics의 평가금액 계산에 필요한 현재가를 market-service BatchQuotes로 조회한다.
 */
@Slf4j
@Component
public class GrpcMarketQuoteClient implements MarketQuoteClient {
    private static final int DEADLINE_SECONDS = 3;

    private final MarketServiceGrpc.MarketServiceBlockingStub stub;

    public GrpcMarketQuoteClient(GrpcChannelFactory channelFactory) {
        this.stub = MarketServiceGrpc.newBlockingStub(
                channelFactory.createChannel("market-service"));
    }

    @Override
    public Map<String, Long> currentPrices(Collection<String> symbols) {
        LinkedHashSet<String> normalizedSymbols = symbols.stream()
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedSymbols.isEmpty()) {
            return Map.of();
        }

        try {
            return stub.withDeadline(Deadline.after(DEADLINE_SECONDS, TimeUnit.SECONDS))
                    .batchQuotes(BatchQuotesRequest.newBuilder()
                            .addAllSymbols(normalizedSymbols)
                            .build())
                    .getQuotesList()
                    .stream()
                    .filter(quote -> quote.getPrice() > 0)
                    .collect(Collectors.toMap(
                            quote -> quote.getSymbol().trim(),
                            quote -> quote.getPrice(),
                            (left, right) -> right));
        } catch (StatusRuntimeException e) {
            log.warn("Market BatchQuotes failed. symbols={}", normalizedSymbols, e);
            return Map.of();
        }
    }
}
