package com.giorgimode.spotmystatus.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.giorgimode.spotmystatus.model.CachedUser;
import com.giorgimode.spotmystatus.persistence.User;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class SpotUtilTest {

    @Test
    void shouldNotCacheUserOnMissingFields() {
        assertThrows(NullPointerException.class, () -> SpotUtil.toCachedUser(new User(), "test_token"));
    }

    @Test
    void shouldCacheUser() {
        User user = new User();
        user.setId("test_user_id");
        user.setTeamId("test_team_id");
        user.setSlackAccessToken("test_slack_token");
        user.setSlackBotToken("test_slack_bot_token");
        user.setSpotifyRefreshToken("test_spotify_token");
        user.setTimezoneOffsetSeconds(1000);
        user.setState(UUID.randomUUID());
        CachedUser cachedUser = SpotUtil.toCachedUser(user, "test_token");
        assertNotNull(cachedUser);
        assertEquals(user.getId(), cachedUser.getId());
        assertEquals(user.getSlackAccessToken(), cachedUser.getSlackAccessToken());
        assertEquals(user.getSpotifyRefreshToken(), cachedUser.getSpotifyRefreshToken());
        assertEquals("test_token", cachedUser.getSpotifyAccessToken());
        assertEquals(1000, cachedUser.getTimezoneOffsetSeconds());
    }

    @Test
    void requireNonBlankShouldBeOk() {
        assertEquals("test", SpotUtil.requireNonBlank("test"));
    }

    @Test
    void requireNonBlankShouldFailOnNull() {
        assertThrows(NullPointerException.class, () -> SpotUtil.requireNonBlank(null));
    }

    @Test
    void requireNonBlankShouldFailOnBlank() {
        assertThrows(NullPointerException.class, () -> SpotUtil.requireNonBlank("  "));
    }


    @Test
    void baseUri() {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setContextPath("/spotmystatus-context");
        ServletRequestAttributes attrs = new ServletRequestAttributes(mockRequest);
        RequestContextHolder.setRequestAttributes(attrs);

        String baseUri = SpotUtil.baseUri("https");
        assertEquals("https://localhost/spotmystatus-context", baseUri);
    }

    @Test
    void shouldGetValueSafely() {
        Map<String, Integer> map = Map.of("one", 1, "two", 2);
        assertEquals(2, SpotUtil.<Integer>safeGet(map, "two"));
        assertNull(SpotUtil.<Integer>safeGet(map, "three"));
    }

    @Test
    void shouldHandleBadValue() {
        assertNull(SpotUtil.<Integer>safeGet(null, "three"));
    }

    @Test
    void shouldGetDefaultValueSafely() {
        Map<String, Integer> map = Map.of("one", 1, "two", 2);
        assertEquals(2, SpotUtil.<Integer>safeGet(map, "two", 88));
        assertEquals(99, SpotUtil.<Integer>safeGet(map, "nine-nine", 99));
    }
}