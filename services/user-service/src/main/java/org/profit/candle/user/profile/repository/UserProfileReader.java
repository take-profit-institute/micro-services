package org.profit.candle.user.profile.repository;

import java.util.Optional;
import org.profit.candle.user.profile.entity.UserProfileEntity;

public interface UserProfileReader {

    Optional<UserProfileEntity> findById(String userId);

    boolean existsById(String userId);

    boolean existsByNickname(String nickname);
}
