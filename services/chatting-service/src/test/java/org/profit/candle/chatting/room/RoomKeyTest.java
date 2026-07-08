package org.profit.candle.chatting.room;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoomKeyTest {

    @Test
    void parse_validRoomId_returnsRoomKey() {
        RoomKey key = RoomKey.parse("005930_12");

        assertThat(key.symbol()).isEqualTo("005930");
        assertThat(key.room()).isEqualTo(12);
    }

    @Test
    void parse_symbolContainingUnderscore_usesLastSeparator() {
        RoomKey key = RoomKey.parse("KR_005930_3");

        assertThat(key.symbol()).isEqualTo("KR_005930");
        assertThat(key.room()).isEqualTo(3);
    }

    @Test
    void parse_missingRoom_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> RoomKey.parse("005930_"));
    }

    @Test
    void parse_missingSeparator_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> RoomKey.parse("005930"));
    }

    @Test
    void derivedValues_matchRedisNamingConvention() {
        RoomKey key = new RoomKey("005930", 1);

        assertThat(key.roomId()).isEqualTo("005930_1");
        assertThat(key.channel()).isEqualTo("chat:005930_1");
        assertThat(key.presenceKey()).isEqualTo("005930_1_presence");
    }
}
