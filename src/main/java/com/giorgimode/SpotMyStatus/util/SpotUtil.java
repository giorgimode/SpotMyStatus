package com.giorgimode.SpotMyStatus.util;

import com.giorgimode.SpotMyStatus.model.CachedUser;
import com.giorgimode.SpotMyStatus.persistence.User;

public class SpotUtil {

    public static CachedUser toCachedUser(User user) {
        CachedUser cachedUser = new CachedUser();
        cachedUser.setId(user.getId());
        cachedUser.setSlackAccessToken(user.getSlackAccessToken());
        cachedUser.setSpotifyAccessToken(user.getSpotifyAccessToken());
        cachedUser.setTimezoneOffsetSeconds(user.getTimezoneOffsetSeconds());
        cachedUser.setDisabled(Boolean.TRUE.equals(user.getDisabled()));
        return cachedUser;
    }
}
