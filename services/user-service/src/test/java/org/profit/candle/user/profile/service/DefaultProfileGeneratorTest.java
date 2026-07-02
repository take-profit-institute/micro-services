package org.profit.candle.user.profile.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.user.profile.repository.UserProfileReader;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultProfileGeneratorTest {

    @Mock UserProfileReader userProfileReader;

    @Test
    void generate_returnsNicknameAndAvatar() {
        when(userProfileReader.existsByNickname(anyString())).thenReturn(false);
        DefaultProfileGenerator generator = new DefaultProfileGenerator(userProfileReader);

        DefaultProfile profile = generator.generate(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        assertThat(profile.nickname()).hasSizeBetween(6, 20);
        assertThat(profile.profileImageUrl()).isNotBlank();
    }

    @Test
    void generate_usesUserIdFallbackWhenRandomCandidatesCollide() {
        when(userProfileReader.existsByNickname(anyString())).thenReturn(true);
        DefaultProfileGenerator generator = new DefaultProfileGenerator(userProfileReader);

        DefaultProfile profile = generator.generate(UUID.fromString("12345678-1234-1234-1234-123456789abc"));

        assertThat(profile.nickname()).isEqualTo("캔들12345678");
    }
}
