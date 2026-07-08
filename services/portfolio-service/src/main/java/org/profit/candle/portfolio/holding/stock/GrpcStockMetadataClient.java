package org.profit.candle.portfolio.holding.stock;

import io.grpc.Deadline;
import io.grpc.StatusRuntimeException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.proto.stock.v1.BatchGetStocksRequest;
import org.profit.candle.proto.stock.v1.MarketType;
import org.profit.candle.proto.stock.v1.StockServiceGrpc;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GrpcStockMetadataClient implements StockMetadataClient {
    private static final int DEADLINE_SECONDS = 3;

    private final StockServiceGrpc.StockServiceBlockingStub stub;

    public GrpcStockMetadataClient(GrpcChannelFactory channelFactory) {
        this.stub = StockServiceGrpc.newBlockingStub(channelFactory.createChannel("stock-service"));
    }

    @Override
    public Map<String, StockMetadata> getMetadata(Collection<String> symbols) {
        LinkedHashSet<String> normalizedSymbols = symbols.stream()
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedSymbols.isEmpty()) {
            return Map.of();
        }

        try {
            return stub.withDeadline(Deadline.after(DEADLINE_SECONDS, TimeUnit.SECONDS))
                    .batchGetStocks(BatchGetStocksRequest.newBuilder()
                            .addAllCodes(normalizedSymbols)
                            .build())
                    .getStocksList()
                    .stream()
                    .collect(Collectors.toMap(
                            stock -> stock.getCode().trim(),
                            stock -> new StockMetadata(
                                    stock.getName(),
                                    stock.getSector(),
                                    marketName(stock.getMarket())),
                            (left, right) -> right));
        } catch (StatusRuntimeException e) {
            log.warn("Stock BatchGetStocks failed. symbols={}", normalizedSymbols, e);
            return Map.of();
        }
    }

    private static String marketName(MarketType market) {
        return switch (market) {
            case KOSPI -> "KOSPI";
            case KOSDAQ -> "KOSDAQ";
            default -> "";
        };
    }
}
