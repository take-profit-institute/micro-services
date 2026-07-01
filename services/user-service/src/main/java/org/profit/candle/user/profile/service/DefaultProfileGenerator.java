package org.profit.candle.user.profile.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.user.profile.repository.UserProfileReader;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DefaultProfileGenerator {

    private static final String[] ADJECTIVES = {
            "차분한", "성실한", "날카로운", "꾸준한", "민첩한",
            "대담한", "신중한", "명석한", "침착한", "빠른"
    };
    private static final String[] NOUNS = {
            "캔들", "투자자", "차트러", "트레이더", "분석가",
            "개미", "수익러", "매매러", "전략가", "불꽃"
    };
    private static final String[] AVATARS = {
            "🐯", "🦊", "🐻", "🐼", "🦁", "🐲", "🚀", "💎", "📈", "👑"
    };
    private static final int MAX_ATTEMPTS = 10;

    private final UserProfileReader userProfileReader;
    private final SecureRandom secureRandom = new SecureRandom();

    public DefaultProfile generate(UUID userId) {
        String nickname = generateUniqueNickname(userId);
        String profileImageUrl = AVATARS[Math.floorMod((userId + ":avatar").hashCode(), AVATARS.length)];
        return new DefaultProfile(nickname, profileImageUrl);
    }

    private String generateUniqueNickname(UUID userId) {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String candidate = randomNickname();
            if (!userProfileReader.existsByNickname(candidate)) {
                return candidate;
            }
        }

        String suffix = userId.toString().replace("-", "").substring(0, 8);
        return "캔들" + suffix;
    }

    private String randomNickname() {
        String adjective = ADJECTIVES[secureRandom.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[secureRandom.nextInt(NOUNS.length)];
        int number = secureRandom.nextInt(10_000);
        return adjective + noun + String.format("%04d", number);
    }
}
