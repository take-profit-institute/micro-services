package org.profit.candle.market.stream;

import org.profit.candle.proto.market.v1.LiveQuote;

/**
 * 단일 뷰어 스트림 추상화. gRPC {@code ServerCallStreamObserver} 를 감싸, 브로커가 관측 가능 여부·
 * 전송·취소 콜백만 다루게 한다(단위 테스트에서 fake 로 대체).
 */
public interface LiveQuoteStream {

    /** 아직 열려 있는가(클라이언트가 취소하지 않았는가). */
    boolean isOpen();

    /** 뷰어에게 한 건 전송. 구현은 스트림별 직렬화(gRPC onNext 비스레드세이프)를 보장한다. */
    void send(LiveQuote quote);

    /** 클라이언트 취소/종료 시 실행할 정리 콜백 등록. */
    void onClose(Runnable handler);
}
