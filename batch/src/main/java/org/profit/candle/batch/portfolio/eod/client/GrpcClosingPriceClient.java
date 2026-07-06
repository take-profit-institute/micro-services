package org.profit.candle.batch.portfolio.eod.client;

import com.google.protobuf.Timestamp;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchException;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchErrorCode;
import org.profit.candle.batch.portfolio.eod.model.ClosingPrice;
import org.profit.candle.proto.stock.v1.ChartServiceGrpc;
import org.profit.candle.proto.stock.v1.GetPreviousCloseRequest;
import org.profit.candle.proto.stock.v1.GetPreviousCloseResponse;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local-eod")
public class GrpcClosingPriceClient implements ClosingPriceClient {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ChartServiceGrpc.ChartServiceBlockingStub stub;
    private final long readDeadlineMillis;
    private final RequestIdGenerator requestIdGenerator;

    public GrpcClosingPriceClient(
            GrpcChannelFactory channelFactory,
            BatchProperties batchProperties,
            RequestIdGenerator requestIdGenerator
    ) {
        this.stub = ChartServiceGrpc.newBlockingStub(
                channelFactory.createChannel(batchProperties.grpc().stockTarget())
        );
        this.readDeadlineMillis = batchProperties.grpc().readDeadlineMillis();
        this.requestIdGenerator = requestIdGenerator;
    }

    @Override
    public List<ClosingPrice> loadClosingPrices(LocalDate businessDate, List<String> symbols) {
        List<ClosingPrice> prices = new ArrayList<>(symbols.size());
        for (String symbol : symbols) {
            prices.add(loadClosingPrice(businessDate, symbol));
        }
        return List.copyOf(prices);
    }

    private ClosingPrice loadClosingPrice(LocalDate businessDate, String symbol) {
        try {
            Instant baseDate = businessDate.plusDays(1).atStartOfDay(KST).toInstant();
            GetPreviousCloseResponse response = stub
                    .withInterceptors(GrpcClientSupport.systemRead(
                            requestIdGenerator.generate()
                    ))
                    .withDeadlineAfter(readDeadlineMillis, TimeUnit.MILLISECONDS)
                    .getPreviousClose(GetPreviousCloseRequest.newBuilder()
                            .setCode(symbol)
                            .setDate(toTimestamp(baseDate))
                            .build());
            if (!response.getCode().equals(symbol) || response.getPrevClose() <= 0) {
                throw new EodBatchException(EodBatchErrorCode.CLOSING_PRICE_INVALID);
            }
            Instant quotedAt = toInstant(response.getPrevOpenTime());
            if (!quotedAt.atZone(KST).toLocalDate().equals(businessDate)) {
                throw new EodBatchException(EodBatchErrorCode.QUOTE_DATE_MISMATCH);
            }
            return new ClosingPrice(symbol, response.getPrevClose(), quotedAt);
        } catch (StatusRuntimeException exception) {
            throw GrpcClientSupport.mapException(exception);
        }
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    private Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
