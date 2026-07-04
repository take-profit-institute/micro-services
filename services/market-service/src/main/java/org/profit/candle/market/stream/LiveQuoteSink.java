package org.profit.candle.market.stream;

import org.profit.candle.market.dto.LiveQuote;

/**
 * WS 수신부가 라이브 시세를 뷰어 팬아웃 경로로 넘기는 출구.
 * 수신부가 proto/gRPC 를 몰라도 되도록 도메인 {@link LiveQuote} 만 받는다.
 */
public interface LiveQuoteSink {
    void publish(LiveQuote quote);
}
