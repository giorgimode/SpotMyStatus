package com.giorgimode.spotmystatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import com.giorgimode.spotmystatus.model.CachedUser;
import com.giorgimode.spotmystatus.model.SlackResponse;
import com.giorgimode.spotmystatus.model.SpotifyCurrentItem;
import com.giorgimode.spotmystatus.model.SpotifyDevice;
import com.giorgimode.spotmystatus.model.SpotifyItem;
import com.giorgimode.spotmystatus.model.SpotifyTokenResponse;
import com.giorgimode.spotmystatus.service.StatusUpdateScheduler;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@ActiveProfiles("test")
class SpotMyStatusIT {

    @MockBean
    private JavaMailSender mailSender;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private LoadingCache<String, CachedUser> userCache;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private StatusUpdateScheduler statusUpdateScheduler;

    @TestConfiguration
    public static class TestConfig {

        @Bean
        public RestTemplate restTemplate() {
            RestTemplate restTemplate = mock(RestTemplate.class);
            SpotifyTokenResponse spotifyTokenResponse = new SpotifyTokenResponse("test", 0, "test_refresh_token");
            spotifyTokenResponse.setAccessToken("spotify_access_token123");
            when(restTemplate.postForEntity(eq("https://fake-spotify.com/api/token"), any(HttpEntity.class), eq(SpotifyTokenResponse.class)))
                .thenReturn(new ResponseEntity<>(spotifyTokenResponse, HttpStatus.OK));
            return restTemplate;
        }

        @Bean
        public ExecutorService cachedThreadPool() {
            ExecutorService executor = mock(ExecutorService.class);
            doAnswer(
                (InvocationOnMock invocation) -> {
                    ((Runnable) invocation.getArguments()[0]).run();
                    return null;
                }
            ).when(executor).execute(any(Runnable.class));
            return executor;
        }
    }


    @BeforeEach
    void setUp() {
        mockSlackPresenceCall();
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
        assertEquals(1900, cachedUser.getSyncEndHour());
        assertEquals("Guns N' Roses - November Rain", cachedUser.getSlackStatus());

        verify(restTemplate).postForEntity(eq("https://fake-spotify.com/api/token"), any(HttpEntity.class), eq(SpotifyTokenResponse.class));
        verify(restTemplate).exchange(eq("https://fake-slack.com/api/users.getPresence"), eq(HttpMethod.GET), any(HttpEntity.class),
            eq(SlackResponse.class));
        verify(restTemplate).exchange(eq("https://fake-slack.com/api/users.profile.get"), eq(HttpMethod.GET), any(HttpEntity.class), eq(
            SlackResponse.class));
        verify(restTemplate).exchange(eq("https://fake-api.spotify.com/v1/me/player?additional_types=track,episode"), eq(HttpMethod.GET),
            any(HttpEntity.class), eq(SpotifyCurrentItem.class));
        verify(restTemplate).postForEntity(eq("https://fake-slack.com/api/users.profile.set"), any(HttpEntity.class), eq(
            SlackResponse.class));
        verify(restTemplate).setMessageConverters(any());
        //noinspection ResultOfMethodCallIgnored
        verify(restTemplate).getMessageConverters();
        verifyNoMoreInteractions(restTemplate);
        verifyNoInteractions(mailSender);
        verifyNoInteractions(clientRegistrationRepository);
    }


    private void mockSlackPresenceCall() {
        SlackResponse slackPresenceResponse = new SlackResponse();
        slackPresenceResponse.setPresence("active");
        when(restTemplate.exchange(eq("https://fake-slack.com/api/users.getPresence"), eq(HttpMethod.GET), any(HttpEntity.class), eq(
            SlackResponse.class))).thenReturn(new ResponseEntity<>(slackPresenceResponse, HttpStatus.OK));
    }

    private void mockSlackProfileCall() {
        SlackResponse slackProfileResponse = new SlackResponse();
        slackProfileResponse.setStatusText("");
        when(restTemplate.exchange(eq("https://fake-slack.com/api/users.profile.get"), eq(HttpMethod.GET), any(HttpEntity.class), eq(
            SlackResponse.class))).thenReturn(new ResponseEntity<>(slackProfileResponse, HttpStatus.OK));
    }

    private void mockSlackUpdateCall() {
        SlackResponse slackStatusUpdateResponse = new SlackResponse();
        slackStatusUpdateResponse.setOk(true);
        when(restTemplate.postForEntity(eq("https://fake-slack.com/api/users.profile.set"), any(HttpEntity.class), eq(
            SlackResponse.class))).thenReturn(new ResponseEntity<>(slackStatusUpdateResponse, HttpStatus.OK));
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
