package com.giorgimode.spotmystatus.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
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
        StatusUpdateScheduler realScheduler = new StatusUpdateScheduler(userCache, slackClient, spotifyClient, spotMyStatusProperties, executor);
        statusUpdateScheduler = spy(realScheduler);
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
    void schedulerShouldHandlePollingException() throws InterruptedException {
        doNothing().when(statusUpdateScheduler).sleep(anyLong());
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
    void schedulerShouldHandlePollingUserException() throws InterruptedException {
        doNothing().when(statusUpdateScheduler).sleep(anyLong());
        mockExecutor();
        when(slackClient.isUserLive(cachedUser)).thenThrow(new RuntimeException());
        statusUpdateScheduler.scheduleFixedDelayTask();
        verifyNoMoreInteractions(slackClient);
        verifyNoInteractions(spotifyClient);
    }

    @Test
    void schedulerShouldSkipPollingWhenUserIsOffline() throws InterruptedException {
        doNothing().when(statusUpdateScheduler).sleep(anyLong());
        mockExecutor();
        when(slackClient.isUserLive(cachedUser)).thenReturn(false);
        statusUpdateScheduler.scheduleFixedDelayTask();
        verify(slackClient).isUserLive(cachedUser);
        verifyNoMoreInteractions(slackClient);
        verifyNoInteractions(spotifyClient);
    }

    @Test
    void schedulerShouldSkipStatusUpdateWhenSpotifyIsNotPlaying() throws InterruptedException {
        doNothing().when(statusUpdateScheduler).sleep(anyLong());
        mockExecutor();
        cachedUser.setCleaned(false);
        when(slackClient.isUserLive(cachedUser)).thenReturn(true);
        SpotifyCurrentItem currentItem = mock(SpotifyCurrentItem.class);
        when(spotifyClient.getCurrentLiveTrack(cachedUser)).thenReturn(Optional.empty());
        statusUpdateScheduler.scheduleFixedDelayTask();
        verify(slackClient).isUserLive(cachedUser);
        verify(slackClient).cleanStatus(cachedUser);
        verify(spotifyClient).getCurrentLiveTrack(cachedUser);
        verifyNoMoreInteractions(slackClient);
        verifyNoMoreInteractions(spotifyClient);
        verifyNoMoreInteractions(currentItem);
    }


    @Test
    void schedulerShouldUpdateStatus() throws InterruptedException {
        doNothing().when(statusUpdateScheduler).sleep(anyLong());
        mockExecutor();
        cachedUser.setSpotifyItems(List.of(SpotifyItem.EPISODE));
        cachedUser.setSpotifyDeviceIds(List.of("device123"));
        SpotifyCurrentItem currentItem = mock(SpotifyCurrentItem.class);
        SpotifyDevice spotifyDevice = new SpotifyDevice();
        spotifyDevice.setId("device123");
        when(slackClient.isUserLive(cachedUser)).thenReturn(true);
        when(spotifyClient.getCurrentLiveTrack(cachedUser)).thenReturn(Optional.of(currentItem));
        statusUpdateScheduler.scheduleFixedDelayTask();
        verify(slackClient).updateAndPersistStatus(cachedUser, currentItem);
        verifyNoMoreInteractions(slackClient);
        verifyNoMoreInteractions(spotifyClient);
        verifyNoMoreInteractions(currentItem);
    }

    private CachedUser createCachedUser() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int syncStartHour = now.getHour();
        CachedUser cachedUser = CachedUser.builder()
                                          .id("user1")
                                          .teamId("teamId")
                                          .slackAccessToken("testSlackToken")
                                          .slackBotToken("testSlackBotToken")
                                          .spotifyRefreshToken("testSpotifyRefreshToken")
                                          .spotifyAccessToken("testSpotifyAccessToken")
                                          .timezoneOffsetSeconds(0)
                                          .syncStartHour(syncStartHour * 100)
                                          .syncEndHour((syncStartHour + 1) * 100)
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