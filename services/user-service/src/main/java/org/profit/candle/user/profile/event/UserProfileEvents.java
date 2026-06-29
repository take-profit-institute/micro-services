package org.profit.candle.user.profile.event;

public final class UserProfileEvents {
    private UserProfileEvents() {}

    public static final String TOPIC = "user.profile-updated.v1";
    public static final String EVENT_TYPE = "UserProfileUpdated";
    public static final int VERSION = 1;
}
