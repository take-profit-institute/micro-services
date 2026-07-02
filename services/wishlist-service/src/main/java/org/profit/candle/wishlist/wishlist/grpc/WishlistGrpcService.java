package org.profit.candle.wishlist.wishlist.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.profit.candle.proto.common.v1.PageResponse;
import org.profit.candle.proto.wishlist.v1.AddWishlistItemRequest;
import org.profit.candle.proto.wishlist.v1.AddWishlistItemResponse;
import org.profit.candle.proto.wishlist.v1.ListWishlistItemsRequest;
import org.profit.candle.proto.wishlist.v1.ListWishlistItemsResponse;
import org.profit.candle.proto.wishlist.v1.RemoveWishlistItemRequest;
import org.profit.candle.proto.wishlist.v1.RemoveWishlistItemResponse;
import org.profit.candle.proto.wishlist.v1.WishlistItem;
import org.profit.candle.proto.wishlist.v1.WishlistServiceGrpc;
import org.profit.candle.wishlist.wishlist.dto.AddWishlistItemCommand;
import org.profit.candle.wishlist.wishlist.dto.ListWishlistItemsResult;
import org.profit.candle.wishlist.wishlist.dto.RemoveWishlistItemCommand;
import org.profit.candle.wishlist.wishlist.dto.WishlistItemResult;
import org.profit.candle.wishlist.wishlist.exception.WishlistErrorCode;
import org.profit.candle.wishlist.wishlist.exception.WishlistException;
import org.profit.candle.wishlist.wishlist.service.WishlistService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WishlistGrpcService extends WishlistServiceGrpc.WishlistServiceImplBase {
    private static final Pattern IDEMPOTENCY_KEY_PATTERN =
            Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");

    private final WishlistService wishlistService;

    @Override
    public void addWishlistItem(
            AddWishlistItemRequest request,
            StreamObserver<AddWishlistItemResponse> observer
    ) {
        try {
            WishlistItemResult result = wishlistService.add(new AddWishlistItemCommand(
                    parseUserId(request.getUserId()),
                    request.getSymbol(),
                    blankToNull(request.getDisplayName()),
                    blankToNull(request.getMarket()),
                    requireIdempotencyKey(request.getCommandMetadata().getIdempotencyKey())
            ));
            observer.onNext(AddWishlistItemResponse.newBuilder()
                    .setItem(toProto(result))
                    .build());
            observer.onCompleted();
        } catch (WishlistException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(Status.INTERNAL
                    .withDescription(WishlistErrorCode.INTERNAL_ERROR.code())
                    .asRuntimeException());
        }
    }

    @Override
    public void removeWishlistItem(
            RemoveWishlistItemRequest request,
            StreamObserver<RemoveWishlistItemResponse> observer
    ) {
        try {
            wishlistService.remove(new RemoveWishlistItemCommand(
                    parseUserId(request.getUserId()),
                    request.getSymbol(),
                    requireIdempotencyKey(request.getCommandMetadata().getIdempotencyKey())
            ));
            observer.onNext(RemoveWishlistItemResponse.newBuilder().build());
            observer.onCompleted();
        } catch (WishlistException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(Status.INTERNAL
                    .withDescription(WishlistErrorCode.INTERNAL_ERROR.code())
                    .asRuntimeException());
        }
    }

    @Override
    public void listWishlistItems(
            ListWishlistItemsRequest request,
            StreamObserver<ListWishlistItemsResponse> observer
    ) {
        try {
            ListWishlistItemsResult result = wishlistService.list(
                    parseUserId(request.getUserId()),
                    request.getPageRequest().getPageSize(),
                    blankToNull(request.getPageRequest().getPageToken())
            );
            ListWishlistItemsResponse.Builder response = ListWishlistItemsResponse.newBuilder()
                    .setPageResponse(PageResponse.newBuilder()
                            .setNextPageToken(result.nextPageToken())
                            .build());
            result.items().forEach(item -> response.addItems(toProto(item)));
            observer.onNext(response.build());
            observer.onCompleted();
        } catch (WishlistException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(Status.INTERNAL
                    .withDescription(WishlistErrorCode.INTERNAL_ERROR.code())
                    .asRuntimeException());
        }
    }

    private static WishlistItem toProto(WishlistItemResult result) {
        WishlistItem.Builder builder = WishlistItem.newBuilder()
                .setId(result.id().toString())
                .setUserId(result.userId().toString())
                .setSymbol(result.symbol())
                .setCreatedAt(toTimestamp(result.createdAt()));
        if (result.displayName() != null) {
            builder.setDisplayName(result.displayName());
        }
        if (result.market() != null) {
            builder.setMarket(result.market());
        }
        return builder.build();
    }

    private static UUID parseUserId(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException e) {
            throw new WishlistException(WishlistErrorCode.INVALID_USER_ID, e);
        }
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null || !IDEMPOTENCY_KEY_PATTERN.matcher(value).matches()) {
            throw new WishlistException(WishlistErrorCode.INVALID_IDEMPOTENCY_KEY);
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    private static Status toGrpcStatus(WishlistException e) {
        if (e.errorCode() instanceof WishlistErrorCode code) {
            return switch (code) {
                case INVALID_USER_ID, INVALID_SYMBOL, INVALID_IDEMPOTENCY_KEY ->
                        Status.INVALID_ARGUMENT.withDescription(code.code());
                case ITEM_NOT_FOUND -> Status.NOT_FOUND.withDescription(code.code());
                case INTERNAL_ERROR -> Status.INTERNAL.withDescription(code.code());
            };
        }
        return Status.INTERNAL.withDescription(e.errorCode().code());
    }
}
