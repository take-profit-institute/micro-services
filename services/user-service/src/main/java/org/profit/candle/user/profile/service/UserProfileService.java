package org.profit.candle.user.profile.service;

import org.profit.candle.user.profile.dto.UpdateProfileCommand;
import org.profit.candle.user.profile.dto.UserProfileResult;

public interface UserProfileService {

    UserProfileResult getProfile(String userId);

    UserProfileResult updateProfile(String userId, UpdateProfileCommand command);
}
