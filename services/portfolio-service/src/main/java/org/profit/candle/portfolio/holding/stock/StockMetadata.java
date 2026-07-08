package org.profit.candle.portfolio.holding.stock;

/**
 * stock-service에서 조회한 종목 기준정보. 보유종목(HoldingEntity)의 name/sector/market을 채우는 데 쓴다.
 * 섹터의 유일 권위 소스는 stock-service이므로 portfolio는 이 값을 통해서만 섹터를 얻는다.
 */
public record StockMetadata(String name, String sector, String market) {

    public static final StockMetadata EMPTY = new StockMetadata("", "", "");

    /** 섹터가 비어 있으면 사실상 미확보 상태로 본다(도넛 집계의 핵심 키가 섹터라서). */
    public boolean isBlank() {
        return sector == null || sector.isBlank();
    }
}
