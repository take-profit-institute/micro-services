package org.profit.candle.stock.catalog.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.proto.common.v1.Audit;
import org.profit.candle.proto.stock.v1.BatchGetStocksRequest;
import org.profit.candle.proto.stock.v1.BatchGetStocksResponse;
import org.profit.candle.proto.stock.v1.DataSource;
import org.profit.candle.proto.stock.v1.GetStockRequest;
import org.profit.candle.proto.stock.v1.GetStockResponse;
import org.profit.candle.proto.stock.v1.ListingStatus;
import org.profit.candle.proto.stock.v1.MarketType;
import org.profit.candle.proto.stock.v1.SearchStocksRequest;
import org.profit.candle.proto.stock.v1.SearchStocksResponse;
import org.profit.candle.proto.stock.v1.Stock;
import org.profit.candle.proto.stock.v1.StockDetail;
import org.profit.candle.proto.stock.v1.StockFinancials;
import org.profit.candle.proto.stock.v1.StockServiceGrpc;
import org.profit.candle.proto.stock.v1.StockSort;
import org.profit.candle.proto.stock.v1.SyncStocksRequest;
import org.profit.candle.proto.stock.v1.SyncStocksResponse;
import org.profit.candle.stock.catalog.dto.StockDataSource;
import org.profit.candle.stock.catalog.dto.StockDetailResult;
import org.profit.candle.stock.catalog.dto.StockFinancialsResult;
import org.profit.candle.stock.catalog.dto.StockResult;
import org.profit.candle.stock.catalog.dto.StockSearchCriteria;
import org.profit.candle.stock.catalog.exception.StockErrorCode;
import org.profit.candle.stock.catalog.service.StockCatalogService;
import org.profit.candle.stock.catalog.service.StockIngestionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;

/** gRPC 진입점: 요청 파싱·검증, service 호출, protobuf 변환만 담당한다. */
@Component
@RequiredArgsConstructor
public class StockGrpcService extends StockServiceGrpc.StockServiceImplBase {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final StockCatalogService catalogService;
    private final StockIngestionService ingestionService;

    @Override
    public void searchStocks(SearchStocksRequest request, StreamObserver<SearchStocksResponse> observer) {
        try {
            int page = Math.max(0, request.getPage());
            int size = request.getSize() <= 0 ? DEFAULT_SIZE : Math.min(request.getSize(), MAX_SIZE);
            Pageable pageable = PageRequest.of(page, size, sortOf(request.getSort()));

            StockSearchCriteria criteria = new StockSearchCriteria(
                    request.getQuery(),
                    marketToString(request.getMarket()),
                    request.getSector(),
                    statusFilter(request.getStatus()));

            Page<StockResult> result = catalogService.search(criteria, pageable);

            SearchStocksResponse.Builder builder = SearchStocksResponse.newBuilder()
                    .setTotalElements(result.getTotalElements())
                    .setTotalPages(result.getTotalPages())
                    .setPage(page)
                    .setSize(size);
            result.getContent().forEach(s -> builder.addStocks(toProtoStock(s)));
            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (CandleException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(toInternalStatus().asRuntimeException());
        }
    }

    @Override
    public void getStock(GetStockRequest request, StreamObserver<GetStockResponse> observer) {
        try {
            StockDetailResult detail = catalogService.getStock(request.getCode(), request.getAllowFallback());
            observer.onNext(GetStockResponse.newBuilder()
                    .setStock(toProtoDetail(detail))
                    .setSource(detail.source() == StockDataSource.KIWOOM ? DataSource.KIWOOM : DataSource.DB)
                    .build());
            observer.onCompleted();
        } catch (CandleException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(toInternalStatus().asRuntimeException());
        }
    }

    @Override
    public void batchGetStocks(BatchGetStocksRequest request, StreamObserver<BatchGetStocksResponse> observer) {
        try {
            BatchGetStocksResponse.Builder builder = BatchGetStocksResponse.newBuilder();
            catalogService.batchGet(request.getCodesList()).forEach(s -> builder.addStocks(toProtoStock(s)));
            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (CandleException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(toInternalStatus().asRuntimeException());
        }
    }

    @Override
    public void syncStocks(SyncStocksRequest request, StreamObserver<SyncStocksResponse> observer) {
        try {
            int upserted = ingestionService.syncMarket(marketToString(request.getMarket()));
            observer.onNext(SyncStocksResponse.newBuilder()
                    .setUpserted(upserted)
                    .setTotal(upserted)
                    .build());
            observer.onCompleted();
        } catch (CandleException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(toInternalStatus().asRuntimeException());
        }
    }

    // ── 매핑 ──────────────────────────────────────────────────────────

    private Stock toProtoStock(StockResult s) {
        Stock.Builder b = Stock.newBuilder()
                .setCode(s.code())
                .setName(s.name())
                .setMarket(stringToMarket(s.marketType()))
                .setSector(s.sector() == null ? "" : s.sector())
                .setMarketCap(s.marketCap() == null ? 0L : s.marketCap())
                .setSharesOutstanding(s.sharesOutstanding() == null ? 0L : s.sharesOutstanding())
                .setStatus(stringToStatus(s.listingStatus()));
        if (s.listedAt() != null) {
            b.setListedAt(toTimestamp(s.listedAt().atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        b.setAudit(auditOf(s.createdAt(), s.updatedAt()));
        return b.build();
    }

    private StockDetail toProtoDetail(StockDetailResult detail) {
        StockDetail.Builder b = StockDetail.newBuilder().setStock(toProtoStock(detail.stock()));
        StockFinancialsResult f = detail.financials();
        if (f != null) {
            b.setFinancials(StockFinancials.newBuilder()
                    .setRevenue(f.revenue() == null ? 0L : f.revenue())
                    .setOperatingProfit(f.operatingProfit() == null ? 0L : f.operatingProfit())
                    .setNetIncome(f.netIncome() == null ? 0L : f.netIncome())
                    .setPer(decimalToString(f.per()))
                    .setPbr(decimalToString(f.pbr()))
                    .setRoe(decimalToString(f.roe()))
                    .setFiscalPeriod(f.fiscalPeriod())
                    .build());
        }
        return b.build();
    }

    /** 정렬은 안정적이어야 한다(§13) — 동률 대비 고유키 stock_code 를 보조 정렬로 부여한다. */
    private static Sort sortOf(StockSort sort) {
        Sort byCode = Sort.by(Sort.Direction.ASC, "stockCode");
        return switch (sort) {
            case NAME_ASC -> Sort.by(Sort.Direction.ASC, "stockName").and(byCode);
            case MARKET_CAP_DESC -> Sort.by(Sort.Direction.DESC, "marketCap").and(byCode);
            default -> byCode;
        };
    }

    private static String marketToString(MarketType market) {
        return switch (market) {
            case KOSPI -> "KOSPI";
            case KOSDAQ -> "KOSDAQ";
            default -> null;
        };
    }

    private static MarketType stringToMarket(String market) {
        if (market == null) {
            return MarketType.MARKET_TYPE_UNSPECIFIED;
        }
        return switch (market) {
            case "KOSPI" -> MarketType.KOSPI;
            case "KOSDAQ" -> MarketType.KOSDAQ;
            default -> MarketType.MARKET_TYPE_UNSPECIFIED;
        };
    }

    /** 목록 검색 필터: 미지정이면 상장(LISTED) 기본. */
    private static String statusFilter(ListingStatus status) {
        return status == ListingStatus.LISTING_STATUS_UNSPECIFIED ? "LISTED" : status.name();
    }

    private static ListingStatus stringToStatus(String status) {
        if (status == null) {
            return ListingStatus.LISTING_STATUS_UNSPECIFIED;
        }
        try {
            return ListingStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return ListingStatus.LISTING_STATUS_UNSPECIFIED;
        }
    }

    private static String decimalToString(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static Audit auditOf(Instant createdAt, Instant updatedAt) {
        Audit.Builder b = Audit.newBuilder();
        if (createdAt != null) {
            b.setCreatedAt(toTimestamp(createdAt));
        }
        if (updatedAt != null) {
            b.setUpdatedAt(toTimestamp(updatedAt));
        }
        return b.build();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    private Status toGrpcStatus(CandleException e) {
        if (e.errorCode() instanceof StockErrorCode code) {
            return switch (code) {
                case STOCK_NOT_FOUND -> Status.NOT_FOUND.withDescription(code.code());
            };
        }
        return Status.INTERNAL.withDescription(e.getMessage());
    }

    private Status toInternalStatus() {
        return Status.INTERNAL.withDescription("INTERNAL");
    }
}
