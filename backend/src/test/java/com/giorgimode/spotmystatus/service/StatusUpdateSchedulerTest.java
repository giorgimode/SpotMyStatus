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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatusUpdateSchedulerTest {

    @Mock
    private ExecutorService executor;

    @Mock
    private SlackClient slackClient;

    @Mock
    private SpotifyClient spotifyClient;
    private LoadingCache<String, CachedUser> userCache;
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
        mockExecutor();
        when(slackClient.isUserOffline(cachedUser)).thenThrow(new RuntimeException());
        statusUpdateScheduler.scheduleFixedDelayTask();
        verify(slackClient).isUserOffline(cachedUser);
        verifyNoMoreInteractions(slackClient);
        verifyNoInteractions(spotifyClient);
    }

    @Test
    void schedulerShouldSkipPollingForDisabledUser() {
        mockExecutor();
        cachedUser.setDisabled(true);
        userCache.put("user1", cachedUser);
        statusUpdateScheduler.scheduleFixedDelayTask();
        verifyNoInteractions(slackClient);
        verifyNoInteractions(spotifyClient);
    }

    @Test
    void schedulerShouldSkipPollingWhenUserIsOffline() {
        mockExecutor();
        when(slackClient.isUserOffline(cachedUser)).thenReturn(true);
        statusUpdateScheduler.scheduleFixedDelayTask();
        verify(slackClient).isUserOffline(cachedUser);
        verifyNoMoreInteractions(slackClient);
        verifyNoInteractions(spotifyClient);
    }

    @Test
    void schedulerShouldSkipPollingWhenStatusHasBeenManuallyChanged() {
        mockExecutor();
        when(slackClient.statusHasBeenManuallyChanged(cachedUser)).thenReturn(true);
        statusUpdateScheduler.scheduleFixedDelayTask();
        verify(slackClient).isUserOffline(cachedUser);
        verify(slackClient).statusHasBeenManuallyChanged(cachedUser);
        verifyNoMoreInteractions(slackClient);
        verifyNoMoreInteractions(spotifyClient);
    }

    @Test
    void schedulerShouldSkipStatusUpdateWhenSpotifyIsNotPlaying() {
        mockExecutor();
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
        mockExecutor();
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
        mockExecutor();
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
        mockExecutor();
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
        CachedUser cachedUser = CachedUser.builder()
                                          .id("user1")
                                          .slackAccessToken("testSlackToken")
                                          .slackBotToken("testSlackBotToken")
                                          .spotifyRefreshToken("testSpotifyRefreshToken")
                                          .spotifyAccessToken("testSpotifyAccessToken")
                                          .build();
        userCache.put("user1", cachedUser);
        return cachedUser;
    }


    private void mockExecutor() {
        doAnswer(
            (InvocationOnMock invocation) -> {
                ((Runnable) invocation.getArguments()[0]).run();
                return null;
            }
        ).when(executor).execute(any(Runnable.class));
    }
}