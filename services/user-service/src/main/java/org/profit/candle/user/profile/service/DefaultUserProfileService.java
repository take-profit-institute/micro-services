package org.profit.candle.user.profile.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.user.profile.dto.UpdateProfileCommand;
import org.profit.candle.user.profile.dto.UserProfileResult;
import org.profit.candle.user.profile.entity.UserProfileEntity;
import org.profit.candle.user.profile.event.OutboxWriter;
import org.profit.candle.user.profile.exception.UserErrorCode;
import org.profit.candle.user.profile.exception.UserException;
import org.profit.candle.user.profile.repository.UserProfileReader;
import org.profit.candle.user.profile.repository.UserProfileWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultUserProfileService implements UserProfileService {

    private final UserProfileReader userProfileReader;
    private final UserProfileWriter userProfileWriter;
    private final OutboxWriter outboxWriter;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResult getProfile(String userId) {
        UserProfileEntity profile = userProfileReader.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        return UserProfileResult.from(profile);
    }

    @Override
    @Transactional
    public UserProfileResult updateProfile(String userId, UpdateProfileCommand command) {
        UserProfileEntity profile = userProfileReader.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        profile.updateProfile(command.nickname(), command.profileImageUrl());
        UserProfileResult result = UserProfileResult.from(userProfileWriter.save(profile));
        outboxWriter.writeUserProfileUpdated(result);
        return result;
    }
}
