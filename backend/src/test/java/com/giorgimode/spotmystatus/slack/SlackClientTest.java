package com.giorgimode.spotmystatus.slack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.giorgimode.spotmystatus.helpers.SpotMyStatusProperties;
import com.giorgimode.spotmystatus.model.CachedUser;
import com.giorgimode.spotmystatus.model.SlackResponse;
import com.giorgimode.spotmystatus.persistence.UserRepository;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class SlackClientTest {

    private static final String TEST_USER_ID = "userId";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SpotMyStatusProperties configProperties;

    @Mock
    private LoadingCache<String, CachedUser> userCache;

    @InjectMocks
    private SlackClient slackClient;

    @Test
    void shouldInvalidateAndNotifyUser() {
        CachedUser cachedUser = createCachedUser();
        when(configProperties.getSlackUri()).thenReturn("https://fake-slack.com");
        when(userCache.getIfPresent(TEST_USER_ID)).thenReturn(cachedUser);
        slackClient.invalidateAndNotifyUser(TEST_USER_ID);
        verify(userCache).invalidate(TEST_USER_ID);
        verify(userRepository).deleteById(TEST_USER_ID);
        verify(restTemplate).postForEntity(eq("https://fake-slack.com/api/chat.postMessage"), any(HttpEntity.class), eq(
            String.class));
    }

    @Test
    void userIsNotAliveWhenDisabled() {
        CachedUser cachedUser = createCachedUser();
        cachedUser.setDisabled(true);
        assertFalse(slackClient.isUserLive(cachedUser));
    }

    @Test
    void userIsNotAliveDuringOfflineHours() {
        CachedUser cachedUser = createCachedUser();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        cachedUser.setSyncStartHour(now.plusHours(1).getHour() * 100);
        cachedUser.setSyncEndHour(now.plusHours(8).getHour() * 100);
        assertFalse(slackClient.isUserLive(cachedUser));
    }

    @Test
    void userIsNotAliveWhenUserNotActive() {
        when(configProperties.getSlackUri()).thenReturn("https://fake-slack.com");
        when(restTemplate.exchange(eq("https://fake-slack.com/api/users.getPresence"), eq(HttpMethod.GET), any(HttpEntity.class),
            eq(SlackResponse.class))).thenReturn(ResponseEntity.of(Optional.of(new SlackResponse())));
        CachedUser cachedUser = createCachedUser();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        cachedUser.setSyncStartHour(now.minusHours(1).getHour() * 100);
        cachedUser.setSyncEndHour(now.plusHours(8).getHour() * 100);
        assertFalse(slackClient.isUserLive(cachedUser));
    }

    @Test
    void shouldCleanUserStatus() {
        SlackStatusPayload slackStatusUpdateResponse = new SlackStatusPayload();
        slackStatusUpdateResponse.setOk(true);
        when(configProperties.getSlackUri()).thenReturn("https://fake-slack.com");
        when(restTemplate.exchange(eq("https://fake-slack.com/api/users.getPresence"), eq(HttpMethod.GET), any(HttpEntity.class),
            eq(SlackResponse.class))).thenReturn(ResponseEntity.of(Optional.of(new SlackResponse())));
        when(restTemplate.postForEntity(eq("https://fake-slack.com/api/users.profile.set"), any(HttpEntity.class), eq(
            SlackStatusPayload.class))).thenReturn(new ResponseEntity<>(slackStatusUpdateResponse, HttpStatus.OK));
        CachedUser cachedUser = createCachedUser();
        cachedUser.setCleaned(false);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        cachedUser.setSyncStartHour(now.minusHours(1).getHour() * 100);
        cachedUser.setSyncEndHour(now.plusHours(8).getHour() * 100);
        assertFalse(slackClient.isUserLive(cachedUser));
        assertTrue(cachedUser.isCleaned());
    }

    @Test
    void userIsNotAliveWhenStatusManuallyChanged() {
        String newStatus = "new_one";
        SlackStatusPayload slackProfileResponse = new SlackStatusPayload(newStatus, "", 5000L);
        when(restTemplate.exchange(eq("https://fake-slack.com/api/users.profile.get"), eq(HttpMethod.GET), any(HttpEntity.class), eq(
            SlackStatusPayload.class))).thenReturn(new ResponseEntity<>(slackProfileResponse, HttpStatus.OK));

        when(configProperties.getSlackUri()).thenReturn("https://fake-slack.com");
        SlackResponse slackResponse = new SlackResponse();
        slackResponse.setPresence("active");
        when(restTemplate.exchange(eq("https://fake-slack.com/api/users.getPresence"), eq(HttpMethod.GET), any(HttpEntity.class),
            eq(SlackResponse.class))).thenReturn(ResponseEntity.of(Optional.of(slackResponse)));
        CachedUser cachedUser = createCachedUser();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        cachedUser.setSyncStartHour(now.minusHours(1).getHour() * 100);
        cachedUser.setSyncEndHour(now.plusHours(8).getHour() * 100);
        assertFalse(slackClient.isUserLive(cachedUser));
        assertTrue(cachedUser.isManualStatus());
        assertEquals(newStatus, cachedUser.getSlackStatus());
    }

    private CachedUser createCachedUser() {
        return CachedUser.builder()
                         .id(TEST_USER_ID)
                         .teamId("teamId")
                         .slackAccessToken("old_slack_token")
                         .slackBotToken("old_slack_bot_token")
                         .spotifyRefreshToken("old_spotify_refresh_token")
                         .spotifyAccessToken("old_spotify_access_token")
                         .timezoneOffsetSeconds(7200)
                         .syncStartHour(900)
                         .syncEndHour(1100)
                         .build();
    }
}