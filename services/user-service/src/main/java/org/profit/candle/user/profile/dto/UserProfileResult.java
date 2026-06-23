package org.profit.candle.user.profile.dto;

import java.time.Instant;
import org.profit.candle.user.profile.entity.UserProfileEntity;

public record UserProfileResult(
        String userId,
        String email,
        String nickname,
        String profileImageUrl,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public static UserProfileResult from(UserProfileEntity entity) {
        return new UserProfileResult(
                entity.userId(),
                entity.email(),
                entity.nickname(),
                entity.profileImageUrl(),
                entity.deleted(),
                entity.createdAt(),
                entity.updatedAt(),
                entity.version());
    }
}
