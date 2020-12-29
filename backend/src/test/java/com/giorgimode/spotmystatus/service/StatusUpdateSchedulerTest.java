package com.giorgimode.spotmystatus.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import com.giorgimode.spotmystatus.helpers.SpotMyStatusProperties;
import com.giorgimode.spotmystatus.model.CachedUser;
import com.giorgimode.spotmystatus.model.SpotifyCurrentItem;
import com.giorgimode.spotmystatus.model.SpotifyDevice;
import com.giorgimode.spotmystatus.model.SpotifyItem;
import com.giorgimode.spotmystatus.slack.SlackClient;
import com.giorgimode.spotmystatus.spotify.SpotifyClient;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatusUpdateSchedulerTest {

    @Mock
    private ExecutorService executor;
    private LoadingCache<String, CachedUser> userCache;
    @Mock
    private SlackClient slackClient;
    @Mock
    private SpotifyClient spotifyClient;
    private SpotMyStatusProperties spotMyStatusProperties;
    private StatusUpdateScheduler statusUpdateScheduler;

    private CachedUser cachedUser;

    @BeforeEach
    void setUp() {
        spotMyStatusProperties = new SpotMyStatusProperties();
        spotMyStatusProperties.setTimeout(1000);
        userCache = Caffeine.newBuilder()
                            .maximumSize(10_000)
                            .build(key -> createCachedUser());

        doAnswer(
            (InvocationOnMock invocation) -> {
                ((Runnable) invocation.getArguments()[0]).run();
                return null;
            }
        ).when(executor).execute(any(Runnable.class));

        statusUpdateScheduler = new StatusUpdateScheduler(userCache, slackClient, spotifyClient, spotMyStatusProperties, executor);
        cachedUser = createCachedUser();
    }

    @Test
    void schedulerShouldHandleBadCache() {
        statusUpdateScheduler = new StatusUpdateScheduler(null, slackClient, spotifyClient, spotMyStatusProperties, executor);
        statusUpdateScheduler.scheduleFixedDelayTask();
        verifyNoInteractions(slackClient);
        verifyNoInteractions(spotifyClient);
    }

    @Test
    void schedulerShouldHandlePollingException() {
        doAnswer(
            (InvocationOnMock invocation) -> {
                throw new RuntimeException();
            }
        ).when(executor).execute(any(Runnable.class));
        statusUpdateScheduler.scheduleFixedDelayTask();
        statusUpdateScheduler.scheduleFixedDelayTask();
        verifyNoInteractions(slackClient);
        verifyNoInteractions(spotifyClient);
    }

    @Test
    void schedulerShouldHandlePollingUserException() {
        when(slackClient.isUserOffline(cachedUser)).thenThrow(new RuntimeException());
        statusUpdateScheduler.scheduleFixedDelayTask();
        verify(slackClient).isUserOffline(cachedUser);
        verifyNoMoreInteractions(slackClient);
        verifyNoInteractions(spotifyClient);
    }

    @Test
    void schedulerShouldSkipPollingForDisabledUser() {
        cachedUser.setDisabled(true);
        userCache.put("user1", cachedUser);
        statusUpdateScheduler.scheduleFixedDelayTask();
        verifyNoInteractions(slackClient);
        verifyNoInteractions(spotifyClient);
    }

    @Test
    void schedulerShouldSkipPollingDuringOfflineHours() {
        cachedUser.setSyncStartHour(cachedUser.getSyncStartHour() + 1);
        cachedUser.setSyncEndHour(cachedUser.getSyncStartHour() + 2);
        statusUpdateScheduler.scheduleFixedDelayTask();
        verifyNoInteractions(slackClient);
        verifyNoInteractions(spotifyClient);
    }

    @Test
    void schedulerShouldSkipPollingDuringDefaultOfflineHours() {
        spotMyStatusProperties.setSyncStartHr(cachedUser.getSyncStartHour() + 100);
        spotMyStatusProperties.setSyncEndHr(cachedUser.getSyncStartHour() + 200);
        cachedUser.setSyncStartHour(null);
        cachedUser.setSyncEndHour(null);
        statusUpdateScheduler.scheduleFixedDelayTask();
        verifyNoInteractions(slackClient);
        verifyNoInteractions(spotifyClient);
    }

    @Test
    void schedulerShouldSkipPollingWhenUserIsOffline() {
        when(slackClient.isUserOffline(cachedUser)).thenReturn(true);
        statusUpdateScheduler.scheduleFixedDelayTask();
        verify(slackClient).isUserOffline(cachedUser);
        verifyNoMoreInteractions(slackClient);
        verifyNoInteractions(spotifyClient);
    }

    @Test
    void schedulerShouldSkipPollingWhenStatusHasBeenManuallyChanged() {
        when(slackClient.statusHasBeenManuallyChanged(cachedUser)).thenReturn(true);
        statusUpdateScheduler.scheduleFixedDelayTask();
        verify(slackClient).isUserOffline(cachedUser);
        verify(slackClient).statusHasBeenManuallyChanged(cachedUser);
        verifyNoMoreInteractions(slackClient);
        verifyNoMoreInteractions(spotifyClient);
    }

    @Test
    void schedulerShouldSkipStatusUpdateWhenSpotifyIsNotPlaying() {
        cachedUser.setCleaned(false);
        SpotifyCurrentItem currentItem = mock(SpotifyCurrentItem.class);
        when(currentItem.getIsPlaying()).thenReturn(false);
        when(spotifyClient.getCurrentTrack(cachedUser)).thenReturn(Optional.of(currentItem));
        statusUpdateScheduler.scheduleFixedDelayTask();
        verify(slackClient).isUserOffline(cachedUser);
        verify(slackClient).statusHasBeenManuallyChanged(cachedUser);
        verify(slackClient).cleanStatus(cachedUser);
        verify(spotifyClient).getCurrentTrack(cachedUser);
        verifyNoMoreInteractions(slackClient);
        verifyNoMoreInteractions(spotifyClient);
        //noinspection ResultOfMethodCallIgnored
        verify(currentItem).getIsPlaying();
        verifyNoMoreInteractions(currentItem);
    }

    @Test
    void schedulerShouldSkipStatusUpdateWhenSpotifyItemNotEnabled() {
        cachedUser.setCleaned(false);
        cachedUser.setSpotifyItems(List.of(SpotifyItem.TRACK));
        SpotifyCurrentItem currentItem = mock(SpotifyCurrentItem.class);
        when(currentItem.getIsPlaying()).thenReturn(true);
        when(currentItem.getType()).thenReturn("episode");
        when(spotifyClient.getCurrentTrack(cachedUser)).thenReturn(Optional.of(currentItem));
        statusUpdateScheduler.scheduleFixedDelayTask();
        verify(slackClient).isUserOffline(cachedUser);
        verify(slackClient).statusHasBeenManuallyChanged(cachedUser);
        verify(spotifyClient).getCurrentTrack(cachedUser);
        verify(slackClient).cleanStatus(cachedUser);
        verifyNoMoreInteractions(slackClient);
        verifyNoMoreInteractions(spotifyClient);
        //noinspection ResultOfMethodCallIgnored
        verify(currentItem).getIsPlaying();
        //noinspection ResultOfMethodCallIgnored
        verify(currentItem, atLeastOnce()).getType();
        verifyNoMoreInteractions(currentItem);
    }

    @Test
    void schedulerShouldSkipStatusUpdateWhenSpotifyDeviceNotEnabled() {
        cachedUser.setCleaned(false);
        cachedUser.setSpotifyDeviceIds(List.of("device123"));
        SpotifyCurrentItem currentItem = mock(SpotifyCurrentItem.class);
        when(currentItem.getIsPlaying()).thenReturn(true);
        SpotifyDevice spotifyDevice = new SpotifyDevice();
        spotifyDevice.setId("device456");
        when(currentItem.getDevice()).thenReturn(spotifyDevice);
        when(spotifyClient.getCurrentTrack(cachedUser)).thenReturn(Optional.of(currentItem));
        statusUpdateScheduler.scheduleFixedDelayTask();
        verify(slackClient).isUserOffline(cachedUser);
        verify(slackClient).statusHasBeenManuallyChanged(cachedUser);
        verify(slackClient).cleanStatus(cachedUser);
        verify(spotifyClient).getCurrentTrack(cachedUser);
        verifyNoMoreInteractions(slackClient);
        verifyNoMoreInteractions(spotifyClient);
        //noinspection ResultOfMethodCallIgnored
        verify(currentItem).getIsPlaying();
        //noinspection ResultOfMethodCallIgnored
        verify(currentItem).getDevice();
        verifyNoMoreInteractions(currentItem);
    }

    @Test
    void schedulerShouldUpdateStatus() {
        cachedUser.setSpotifyItems(List.of(SpotifyItem.EPISODE));
        cachedUser.setSpotifyDeviceIds(List.of("device123"));
        SpotifyCurrentItem currentItem = mock(SpotifyCurrentItem.class);
        when(currentItem.getIsPlaying()).thenReturn(true);
        when(currentItem.getType()).thenReturn("episode");
        SpotifyDevice spotifyDevice = new SpotifyDevice();
        spotifyDevice.setId("device123");
        when(currentItem.getDevice()).thenReturn(spotifyDevice);
        when(spotifyClient.getCurrentTrack(cachedUser)).thenReturn(Optional.of(currentItem));
        statusUpdateScheduler.scheduleFixedDelayTask();
        verify(spotifyClient).getCurrentTrack(cachedUser);
        verify(slackClient).isUserOffline(cachedUser);
        verify(slackClient).statusHasBeenManuallyChanged(cachedUser);
        verify(slackClient).updateAndPersistStatus(cachedUser, currentItem);
        verifyNoMoreInteractions(slackClient);
        verifyNoMoreInteractions(spotifyClient);
        //noinspection ResultOfMethodCallIgnored
        verify(currentItem, atLeastOnce()).getType();
        //noinspection ResultOfMethodCallIgnored
        verify(currentItem).getIsPlaying();
        //noinspection ResultOfMethodCallIgnored
        verify(currentItem).getDevice();
        verifyNoMoreInteractions(currentItem);
    }

    private CachedUser createCachedUser() {
        OffsetDateTime now = LocalDateTime.now()
                                          .atOffset(ZoneOffset.ofTotalSeconds(0))
                                          .withOffsetSameInstant(ZoneOffset.UTC);
        int syncStartHour = now.getHour();
        CachedUser cachedUser = CachedUser.builder()
                                          .id("test123")
                                          .slackAccessToken("testSlackToken")
                                          .spotifyRefreshToken("testSpotifyRefreshToken")
                                          .spotifyAccessToken("testSpotifyAccessToken")
                                          .timezoneOffsetSeconds(0)
                                          .syncStartHour(syncStartHour * 100)
                                          .syncEndHour((syncStartHour + 1) * 100)
                                          .build();
        userCache.put("user1", cachedUser);
        return cachedUser;
    }
}