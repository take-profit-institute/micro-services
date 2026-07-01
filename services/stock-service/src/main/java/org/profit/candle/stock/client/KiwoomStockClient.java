package org.profit.candle.stock.client;

import java.util.List;
import java.util.Optional;

/** 키움 OpenAPI 종목 기준정보 조회 추상화. 미설정(키 없음) 시 빈 결과를 반환한다. */
public interface KiwoomStockClient {

    /** 단일 종목 기준정보. 없거나 조회 실패 시 empty. */
    Optional<KiwoomStockData> findStock(String code);

    /** 시장 전체 종목 목록(배치 동기화용). marketType 이 null 이면 전체 시장. */
    List<KiwoomStockData> findAllStocksByMarket(String marketType);
}
