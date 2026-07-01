UPDATE user_profiles
SET nickname = '캔들' || substring(replace(user_id, '-', '') from 1 for 8),
    updated_at = now()
WHERE nickname IS NULL OR btrim(nickname) = '';

UPDATE user_profiles
SET profile_image_url = CASE floor(random() * 10)::int
    WHEN 0 THEN '🐯'
    WHEN 1 THEN '🦊'
    WHEN 2 THEN '🐻'
    WHEN 3 THEN '🐼'
    WHEN 4 THEN '🦁'
    WHEN 5 THEN '🐲'
    WHEN 6 THEN '🚀'
    WHEN 7 THEN '💎'
    WHEN 8 THEN '📈'
    ELSE '👑'
END,
    updated_at = now()
WHERE profile_image_url IS NULL OR btrim(profile_image_url) = '';

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_profiles_active_nickname
    ON user_profiles (nickname)
    WHERE deleted = false AND nickname IS NOT NULL;
