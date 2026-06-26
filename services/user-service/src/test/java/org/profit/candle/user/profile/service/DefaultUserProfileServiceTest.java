package org.profit.candle.user.profile.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.user.profile.dto.UpdateProfileCommand;
import org.profit.candle.user.profile.dto.UserProfileResult;
import org.profit.candle.user.profile.entity.UserProfileEntity;
import org.profit.candle.user.profile.event.UserProfileEventPublisher;
import org.profit.candle.user.profile.exception.UserErrorCode;
import org.profit.candle.user.profile.repository.UserProfileReader;
import org.profit.candle.user.profile.repository.UserProfileWriter;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultUserProfileServiceTest {

    @Mock UserProfileReader userProfileReader;
    @Mock UserProfileWriter userProfileWriter;
    @Mock UserProfileEventPublisher eventPublisher;
    @InjectMocks DefaultUserProfileService service;

    private static final String USER_ID = "user-1";

    @Test
    void getProfile_returnsResult() {
        UserProfileEntity entity = new UserProfileEntity(USER_ID, "a@b.com", "nick", "url");
        when(userProfileReader.findById(USER_ID)).thenReturn(Optional.of(entity));

        UserProfileResult result = service.getProfile(USER_ID);

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.email()).isEqualTo("a@b.com");
        assertThat(result.nickname()).isEqualTo("nick");
        assertThat(result.deleted()).isFalse();
    }

    @Test
    void getProfile_notFound_throwsUserNotFound() {
        when(userProfileReader.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(USER_ID))
                .isInstanceOf(CandleException.class)
                .satisfies(ex -> assertThat(((CandleException) ex).errorCode())
                        .isEqualTo(UserErrorCode.USER_NOT_FOUND));
    }

    @Test
    void updateProfile_happyPath_savesAndPublishesEvent() {
        UserProfileEntity entity = new UserProfileEntity(USER_ID, "a@b.com", "old", "old-url");
        when(userProfileReader.findById(USER_ID)).thenReturn(Optional.of(entity));
        when(userProfileWriter.save(entity)).thenReturn(entity);

        UpdateProfileCommand command = new UpdateProfileCommand("new", "new-url");
        service.updateProfile(USER_ID, command);

        verify(userProfileWriter).save(entity);
        verify(eventPublisher).publishUserProfileUpdated(any(UserProfileResult.class));
    }

    @Test
    void updateProfile_notFound_throwsUserNotFound() {
        when(userProfileReader.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProfile(USER_ID, new UpdateProfileCommand("nick", "url")))
                .isInstanceOf(CandleException.class)
                .satisfies(ex -> assertThat(((CandleException) ex).errorCode())
                        .isEqualTo(UserErrorCode.USER_NOT_FOUND));

        verify(eventPublisher, never()).publishUserProfileUpdated(any());
    }

    @Test
    void updateProfile_nicknameTooLong_throwsNicknameTooLong() {
        String tooLong = "a".repeat(51);

        assertThatThrownBy(() -> service.updateProfile(USER_ID, new UpdateProfileCommand(tooLong, null)))
                .isInstanceOf(CandleException.class)
                .satisfies(ex -> assertThat(((CandleException) ex).errorCode())
                        .isEqualTo(UserErrorCode.NICKNAME_TOO_LONG));

        verify(eventPublisher, never()).publishUserProfileUpdated(any());
    }

    @Test
    void updateProfile_nicknameAtMaxLength_succeeds() {
        String maxLength = "a".repeat(50);
        UserProfileEntity entity = new UserProfileEntity(USER_ID, "a@b.com", null, null);
        when(userProfileReader.findById(USER_ID)).thenReturn(Optional.of(entity));
        when(userProfileWriter.save(entity)).thenReturn(entity);

        service.updateProfile(USER_ID, new UpdateProfileCommand(maxLength, null));

        verify(userProfileWriter).save(entity);
    }

    @Test
    void updateProfile_profileImageUrlTooLong_throwsUrlTooLong() {
        String tooLong = "x".repeat(501);

        assertThatThrownBy(() -> service.updateProfile(USER_ID, new UpdateProfileCommand(null, tooLong)))
                .isInstanceOf(CandleException.class)
                .satisfies(ex -> assertThat(((CandleException) ex).errorCode())
                        .isEqualTo(UserErrorCode.PROFILE_IMAGE_URL_TOO_LONG));
    }

    @Test
    void updateProfile_nullNicknameAndUrl_skipsValidationAndSaves() {
        UserProfileEntity entity = new UserProfileEntity(USER_ID, "a@b.com", "nick", "url");
        when(userProfileReader.findById(USER_ID)).thenReturn(Optional.of(entity));
        when(userProfileWriter.save(entity)).thenReturn(entity);

        service.updateProfile(USER_ID, new UpdateProfileCommand(null, null));

        verify(userProfileWriter).save(entity);
        verify(eventPublisher).publishUserProfileUpdated(any());
    }
}
