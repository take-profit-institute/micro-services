package org.profit.candle.user.profile.entity;

import org.junit.jupiter.api.Test;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.user.profile.exception.UserErrorCode;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import java.time.temporal.ChronoUnit;

class UserProfileEntityTest {

    @Test
    void constructor_setsFieldsAndDefaultsDeletedFalse() {
        UserProfileEntity entity = new UserProfileEntity("u1", "a@b.com", "nick", "http://img");

        assertThat(entity.userId()).isEqualTo("u1");
        assertThat(entity.email()).isEqualTo("a@b.com");
        assertThat(entity.nickname()).isEqualTo("nick");
        assertThat(entity.profileImageUrl()).isEqualTo("http://img");
        assertThat(entity.deleted()).isFalse();
        assertThat(entity.updatedAt()).isNotNull();
    }

    @Test
    void updateProfile_replacesNicknameAndProfileImageUrl() {
        UserProfileEntity entity = new UserProfileEntity("u1", "a@b.com", "old-nick", "old-url");

        entity.updateProfile("new-nick", "new-url");

        assertThat(entity.nickname()).isEqualTo("new-nick");
        assertThat(entity.profileImageUrl()).isEqualTo("new-url");
    }

    @Test
    void updateProfile_refreshesUpdatedAt() throws InterruptedException {
        UserProfileEntity entity = new UserProfileEntity("u1", "a@b.com", "nick", "url");
        Instant before = entity.updatedAt();
        Thread.sleep(2);

        entity.updateProfile("new-nick", "new-url");

        assertThat(entity.updatedAt()).isAfter(before);
    }

    @Test
    void updateProfile_allowsNullValues() {
        UserProfileEntity entity = new UserProfileEntity("u1", "a@b.com", "nick", "url");

        entity.updateProfile(null, null);

        assertThat(entity.nickname()).isNull();
        assertThat(entity.profileImageUrl()).isNull();
    }

    @Test
    void delete_setsDeletedTrue() {
        UserProfileEntity entity = new UserProfileEntity("u1", "a@b.com", "nick", "url");
        assertThat(entity.deleted()).isFalse();

        entity.delete();

        assertThat(entity.deleted()).isTrue();
    }

    @Test
    void delete_refreshesUpdatedAt() throws InterruptedException {
        UserProfileEntity entity = new UserProfileEntity("u1", "a@b.com", "nick", "url");
        Instant before = entity.updatedAt();
        Thread.sleep(2);

        entity.delete();

        assertThat(entity.updatedAt()).isAfter(before);
    }

    @Test
    void delete_isIdempotent() {
        UserProfileEntity entity = new UserProfileEntity("u1", "a@b.com", "nick", "url");
        entity.delete();
        entity.delete();

        assertThat(entity.deleted()).isTrue();
    }

    @Test
    void updateProfile_nicknameTooLong_throwsNicknameTooLong() {
        UserProfileEntity entity = new UserProfileEntity("u1", "a@b.com", "nick", "url");
        String tooLong = "a".repeat(51);

        assertThatThrownBy(() -> entity.updateProfile(tooLong, null))
                .isInstanceOf(CandleException.class)
                .satisfies(ex -> assertThat(((CandleException) ex).errorCode())
                        .isEqualTo(UserErrorCode.NICKNAME_TOO_LONG));
    }

    @Test
    void updateProfile_nicknameAtMaxLength_succeeds() {
        UserProfileEntity entity = new UserProfileEntity("u1", "a@b.com", "nick", "url");
        String maxLength = "a".repeat(50);

        entity.updateProfile(maxLength, null);

        assertThat(entity.nickname()).isEqualTo(maxLength);
    }

    @Test
    void updateProfile_profileImageUrlTooLong_throwsUrlTooLong() {
        UserProfileEntity entity = new UserProfileEntity("u1", "a@b.com", "nick", "url");
        String tooLong = "x".repeat(501);

        assertThatThrownBy(() -> entity.updateProfile(null, tooLong))
                .isInstanceOf(CandleException.class)
                .satisfies(ex -> assertThat(((CandleException) ex).errorCode())
                        .isEqualTo(UserErrorCode.PROFILE_IMAGE_URL_TOO_LONG));
    }

    @Test
    void updateProfile_profileImageUrlAtMaxLength_succeeds() {
        UserProfileEntity entity = new UserProfileEntity("u1", "a@b.com", "nick", "url");
        String maxLength = "x".repeat(500);

        entity.updateProfile(null, maxLength);

        assertThat(entity.profileImageUrl()).isEqualTo(maxLength);
    }
}
