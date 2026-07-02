package org.profit.candle.trading.client;

import io.grpc.StatusRuntimeException;

/**
 * ChartService gRPC 호출 실패 시 던지는 예외.
 * 컨벤션 8장: "오류를 감쌀 때는 원인을 보존한다."
 * 호출 측(DefaultReservationBatchService)에서 catch해 FAILED 처리하거나 skip한다.
 */
public class ChartServiceException extends RuntimeException {

    private final String symbol;

    public ChartServiceException(String symbol, StatusRuntimeException cause) {
        super("ChartService.GetPreviousClose 호출 실패 — symbol: " + symbol
                + ", status: " + cause.getStatus(), cause);
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
