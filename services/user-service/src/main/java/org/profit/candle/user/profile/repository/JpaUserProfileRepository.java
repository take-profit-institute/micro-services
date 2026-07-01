package org.profit.candle.user.profile.repository;

import java.util.Optional;
import org.profit.candle.user.profile.entity.UserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaUserProfileRepository
        extends JpaRepository<UserProfileEntity, String>, UserProfileReader, UserProfileWriter {

    @Override
    Optional<UserProfileEntity> findById(String userId);

    @Override
    boolean existsById(String userId);

    @Override
    boolean existsByNickname(String nickname);

    @Override
    UserProfileEntity save(UserProfileEntity profile);
}
