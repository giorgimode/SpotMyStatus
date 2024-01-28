package com.giorgimode.spotmystatus;

import com.giorgimode.spotmystatus.SpotMyStatusITBase.SpotMyStatusTestConfig;
import com.giorgimode.spotmystatus.model.*;
import com.giorgimode.spotmystatus.service.StatusUpdateScheduler;
import com.giorgimode.spotmystatus.slack.SlackStatusPayload;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Import(SpotMyStatusTestConfig.class)
@Transactional
class SpotMyStatusIT extends SpotMyStatusITBase {

    @Autowired
    private LoadingCache<String, CachedUser> userCache;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private StatusUpdateScheduler statusUpdateScheduler;


    @BeforeEach
    void setUp() {
        mockSlackProfileCall();
        mockSpotifyCall();
        mockSlackUpdateCall();
    }

    @Test
    void shouldUpdateUserStatus() {
        statusUpdateScheduler.scheduleFixedDelayTask();
        assertNotNull(userCache);
        assertEquals(1, userCache.asMap().size());
        CachedUser cachedUser = userCache.getIfPresent("user123");
        assertNotNull(cachedUser);
        assertEquals("user123", cachedUser.getId());
        assertEquals(3600, cachedUser.getTimezoneOffsetSeconds());
        assertFalse(cachedUser.isManualStatus());
        assertFalse(cachedUser.isDisabled());
        assertFalse(cachedUser.isCleaned());
        assertNotNull(cachedUser.getUpdatedAt());
        assertEquals("slack_access_token123", cachedUser.getSlackAccessToken());
        assertEquals("spotify_access_token123", cachedUser.getSpotifyAccessToken());
        assertEquals("spotify_refresh_token123", cachedUser.getSpotifyRefreshToken());
        assertTrue(cachedUser.getEmojis().containsAll(List.of("headphones", "musical_note", "notes")));
        assertTrue(cachedUser.getSpotifyItems().contains(SpotifyItem.TRACK));
        assertTrue(cachedUser.getSpotifyDeviceIds().contains("macbookId123"));
        assertEquals(600, cachedUser.getSyncStartHour());
        assertEquals(559, cachedUser.getSyncEndHour());
        assertEquals("Guns N' Roses - November Rain", cachedUser.getSlackStatus());

        verify(restTemplate).postForEntity(eq("https://fake-spotify.com/api/token"), any(HttpEntity.class), eq(SpotifyTokenResponse.class));
        verify(restTemplate).exchange(eq("https://fake-slack.com/api/users.getPresence"), eq(HttpMethod.GET), any(HttpEntity.class),
            eq(SlackResponse.class));
        verify(restTemplate).exchange(eq("https://fake-slack.com/api/users.profile.get"), eq(HttpMethod.GET), any(HttpEntity.class), eq(
            SlackStatusPayload.class));
        verify(restTemplate).exchange(eq("https://fake-api.spotify.com/v1/me/player?additional_types=track,episode"), eq(HttpMethod.GET),
            any(HttpEntity.class), eq(SpotifyCurrentItem.class));
        verify(restTemplate).postForEntity(eq("https://fake-slack.com/api/users.profile.set"), any(HttpEntity.class), eq(
            SlackStatusPayload.class));
        verifyNoInteractions(mailSender);
        verifyNoInteractions(clientRegistrationRepository);
    }


    private void mockSlackProfileCall() {
        SlackStatusPayload slackProfileResponse = new SlackStatusPayload("", "", 5000L);
        when(restTemplate.exchange(eq("https://fake-slack.com/api/users.profile.get"), eq(HttpMethod.GET), any(HttpEntity.class), eq(
            SlackStatusPayload.class))).thenReturn(new ResponseEntity<>(slackProfileResponse, HttpStatus.OK));
    }

    private void mockSlackUpdateCall() {
        SlackStatusPayload slackStatusUpdateResponse = new SlackStatusPayload();
        slackStatusUpdateResponse.setOk(true);
        when(restTemplate.postForEntity(eq("https://fake-slack.com/api/users.profile.set"), any(HttpEntity.class), eq(
            SlackStatusPayload.class))).thenReturn(new ResponseEntity<>(slackStatusUpdateResponse, HttpStatus.OK));
    }

    private void mockSpotifyCall() {
        SpotifyCurrentItem spotifyCurrentItem = new SpotifyCurrentItem();
        spotifyCurrentItem.setIsPlaying(true);
        SpotifyDevice spotifyDevice = new SpotifyDevice();
        spotifyDevice.setId("macbookId123");
        spotifyCurrentItem.setDevice(spotifyDevice);
        spotifyCurrentItem.setTitle("November Rain");
        spotifyCurrentItem.setType("track");
        spotifyCurrentItem.setArtists(List.of("Guns N' Roses"));
        spotifyCurrentItem.setDurationMs(180000L);
        spotifyCurrentItem.setProgressMs(100000L);
        when(restTemplate.exchange(eq("https://fake-api.spotify.com/v1/me/player?additional_types=track,episode"), eq(HttpMethod.GET),
            any(HttpEntity.class), eq(
                SpotifyCurrentItem.class))).thenReturn(new ResponseEntity<>(spotifyCurrentItem, HttpStatus.OK));
    }

}
