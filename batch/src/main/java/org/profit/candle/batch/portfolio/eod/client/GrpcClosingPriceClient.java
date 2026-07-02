package org.profit.candle.batch.portfolio.eod.client;

import com.google.protobuf.Timestamp;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchException;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchErrorCode;
import org.profit.candle.batch.portfolio.eod.model.ClosingPrice;
import org.profit.candle.proto.market.v1.BatchQuotesRequest;
import org.profit.candle.proto.market.v1.BatchQuotesResponse;
import org.profit.candle.proto.market.v1.MarketServiceGrpc;
import org.profit.candle.proto.market.v1.Quote;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local-eod")
public class GrpcClosingPriceClient implements ClosingPriceClient {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MarketServiceGrpc.MarketServiceBlockingStub stub;
    private final long readDeadlineMillis;
    private final RequestIdGenerator requestIdGenerator;

    public GrpcClosingPriceClient(
            GrpcChannelFactory channelFactory,
            BatchProperties batchProperties,
            RequestIdGenerator requestIdGenerator
    ) {
        this.stub = MarketServiceGrpc.newBlockingStub(
                channelFactory.createChannel(batchProperties.grpc().marketTarget())
        );
        this.readDeadlineMillis = batchProperties.grpc().readDeadlineMillis();
        this.requestIdGenerator = requestIdGenerator;
    }

    @Override
    public List<ClosingPrice> loadClosingPrices(LocalDate businessDate, List<String> symbols) {
        try {
            BatchQuotesResponse response = stub
                    .withInterceptors(GrpcClientSupport.systemRead(
                            requestIdGenerator.generate()
                    ))
                    .withDeadlineAfter(readDeadlineMillis, TimeUnit.MILLISECONDS)
                    .batchQuotes(BatchQuotesRequest.newBuilder().addAllSymbols(symbols).build());
            Map<String, ClosingPrice> prices = new HashMap<>();

            for (Quote quote : response.getQuotesList()) {
                if (quote.getPrice() <= 0) {
                    throw new EodBatchException(EodBatchErrorCode.CLOSING_PRICE_INVALID);
                }
                Instant quotedAt = toInstant(quote.getQuotedAt());
                if (!quotedAt.atZone(KST).toLocalDate().equals(businessDate)) {
                    throw new EodBatchException(EodBatchErrorCode.QUOTE_DATE_MISMATCH);
                }
                prices.put(
                        quote.getSymbol(),
                        new ClosingPrice(
                                quote.getSymbol(),
                                quote.getPrice(),
                                quotedAt
                        )
                );
            }

            for (String symbol : symbols) {
                if (!prices.containsKey(symbol)) {
                    throw new EodBatchException(EodBatchErrorCode.CLOSING_PRICE_INVALID);
                }
            }
            return symbols.stream().map(prices::get).toList();
        } catch (StatusRuntimeException exception) {
            throw GrpcClientSupport.mapException(exception);
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
