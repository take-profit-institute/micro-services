package org.profit.candle.market.grpc;

import io.grpc.stub.ServerCallStreamObserver;
import org.profit.candle.market.stream.LiveQuoteStream;
import org.profit.candle.proto.market.v1.LiveQuote;

/**
 * gRPC {@link ServerCallStreamObserver} 를 {@link LiveQuoteStream} 으로 감싸는 어댑터.
 * onNext 는 스트림당 직렬화한다(gRPC StreamObserver 는 동시 호출에 안전하지 않다).
 */
final class ServerCallLiveQuoteStream implements LiveQuoteStream {

    private final ServerCallStreamObserver<LiveQuote> observer;

    ServerCallLiveQuoteStream(ServerCallStreamObserver<LiveQuote> observer) {
        this.observer = observer;
    }

    @Override
    public boolean isOpen() {
        return !observer.isCancelled();
    }

    @Override
    public synchronized void send(LiveQuote quote) {
        if (observer.isCancelled()) {
            return;
        }
        try {
            observer.onNext(quote);
        } catch (RuntimeException e) {
            // 전송 중 스트림이 끊긴 경우 — onCancelHandler 가 정리하므로 여기서는 무시
        }
    }

    @Override
    public void onClose(Runnable handler) {
        observer.setOnCancelHandler(handler);
    }
}
