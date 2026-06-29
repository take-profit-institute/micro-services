package org.profit.candle.user.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.profit.candle.proto.common.v1.CommandMetadata;
import org.profit.candle.proto.user.v1.UpdateProfileRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHasherTest {

    RequestHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new RequestHasher();
    }

    private UpdateProfileRequest request(String nickname, String idempotencyKey) {
        UpdateProfileRequest.Builder builder = UpdateProfileRequest.newBuilder()
                .setUserId("user-1")
                .setNickname(nickname);
        if (idempotencyKey != null) {
            builder.setCommandMetadata(
                    CommandMetadata.newBuilder().setIdempotencyKey(idempotencyKey).build());
        }
        return builder.build();
    }

    @Test
    void hash_sameInputs_returnsSameHash() {
        UpdateProfileRequest req = request("nick", "key-1");

        String h1 = hasher.hash("op", "actor", req);
        String h2 = hasher.hash("op", "actor", req);

        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void hash_differentIdempotencyKey_returnsSameHash() {
        // idempotency_key is cleared before hashing — different keys should produce identical hashes
        UpdateProfileRequest req1 = request("nick", "key-1");
        UpdateProfileRequest req2 = request("nick", "key-2");

        String h1 = hasher.hash("op", "actor", req1);
        String h2 = hasher.hash("op", "actor", req2);

        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void hash_differentOperation_returnsDifferentHash() {
        UpdateProfileRequest req = request("nick", "key-1");

        String h1 = hasher.hash("op-A", "actor", req);
        String h2 = hasher.hash("op-B", "actor", req);

        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void hash_differentActorId_returnsDifferentHash() {
        UpdateProfileRequest req = request("nick", "key-1");

        String h1 = hasher.hash("op", "actor-1", req);
        String h2 = hasher.hash("op", "actor-2", req);

        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void hash_differentNickname_returnsDifferentHash() {
        UpdateProfileRequest req1 = request("nick-A", "key-1");
        UpdateProfileRequest req2 = request("nick-B", "key-1");

        String h1 = hasher.hash("op", "actor", req1);
        String h2 = hasher.hash("op", "actor", req2);

        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void hash_returnsLowercaseHex64Chars() {
        String hash = hasher.hash("op", "actor", request("nick", null));

        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void hash_noCommandMetadata_doesNotFail() {
        UpdateProfileRequest req = UpdateProfileRequest.newBuilder()
                .setUserId("user-1")
                .setNickname("nick")
                .build();

        assertThat(hasher.hash("op", "actor", req)).hasSize(64);
    }
}
