package com.giorgimode.spotmystatus.helpers;

import static org.apache.commons.lang3.StringUtils.isBlank;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giorgimode.spotmystatus.model.CachedUser;
import com.giorgimode.spotmystatus.persistence.User;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SpotUtil {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static CachedUser toCachedUser(User user, String spotifyAccessToken) {
        return CachedUser.builder()
                         .id(user.getId())
                         .slackAccessToken(user.getSlackAccessToken())
                         .slackBotToken(user.getSlackBotToken())
                         .spotifyRefreshToken(user.getSpotifyRefreshToken())
                         .spotifyAccessToken(spotifyAccessToken)
                         .disabled(user.isDisabled())
                         .emojis(user.getEmojis())
                         .spotifyItems(user.getSpotifyItems())
                         .spotifyDeviceIds(user.getSpotifyDevices())
                         .build();
    }

    public static String requireNonBlank(String string) {
        if (isBlank(string)) {
            throw new NullPointerException();
        }
        return string;
    }

    public static String baseUri(String scheme) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().scheme(scheme).build().toUriString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T safeGet(Map map, String field) {
        try {
            Object o = map.get(field);
            if (o != null) {
                return (T) o;
            }
        } catch (Exception e) {
            log.error("Failed to cast field {} of map {}", field, map);
        }
        return null;
    }

    @SuppressWarnings({"rawtypes"})
    public static <T> T safeGet(Map map, String field, T defaultValue) {
        T o = safeGet(map, field);
        if (o == null) {
            return defaultValue;
        }
        return o;
    }
}
