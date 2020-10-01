package com.giorgimode.SpotMyStatus.util;

import static org.apache.commons.lang3.StringUtils.isBlank;
import com.giorgimode.SpotMyStatus.model.CachedUser;
import com.giorgimode.SpotMyStatus.persistence.User;

public class SpotUtil {

    public static CachedUser toCachedUser(User user) {
        return CachedUser.builder()
                         .id(user.getId())
                         .slackAccessToken(user.getSlackAccessToken())
                         .spotifyAccessToken(user.getSpotifyAccessToken())
                         .timezoneOffsetSeconds(user.getTimezoneOffsetSeconds())
                         .disabled(user.isDisabled())
                         .build();
    }

    public static String requireNonBlank(String string) {
        if (isBlank(string)) {
            throw new NullPointerException();
        }
        return string;
    }
}
