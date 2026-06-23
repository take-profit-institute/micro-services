package org.profit.candle.user.profile.repository;

import org.profit.candle.user.profile.entity.UserProfileEntity;

public interface UserProfileWriter {

    UserProfileEntity save(UserProfileEntity profile);
}
