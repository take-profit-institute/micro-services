package org.profit.candle.user.profile.service;

import org.profit.candle.user.profile.dto.UpdateProfileCommand;
import org.profit.candle.user.profile.dto.UserPageResult;
import org.profit.candle.user.profile.dto.UserProfileResult;
import org.profit.candle.user.profile.dto.UserSearchQuery;

public interface UserProfileService {

    UserProfileResult getProfile(String userId);

    UserProfileResult updateProfile(String userId, UpdateProfileCommand command);

    /** 관리자 콘솔용 회원 목록. 최신 가입순 정렬. */
    UserPageResult listUsers(UserSearchQuery query);
}
