package com.giorgimode.spotmystatus.service;

import com.giorgimode.spotmystatus.helpers.SpotMyStatusProperties;
import com.giorgimode.spotmystatus.model.CachedUser;
import com.giorgimode.spotmystatus.slack.SlackClient;
import com.giorgimode.spotmystatus.spotify.SpotifyClient;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StatusUpdateScheduler {

    private final ExecutorService executor;
    private final LoadingCache<String, CachedUser> userCache;
    private final SlackClient slackClient;
    private final SpotifyClient spotifyClient;
    private final SpotMyStatusProperties spotMyStatusProperties;

    public StatusUpdateScheduler(LoadingCache<String, CachedUser> userCache, SlackClient slackClient,
        SpotifyClient spotifyClient, SpotMyStatusProperties spotMyStatusProperties, ExecutorService executor) {
        this.userCache = userCache;
        this.slackClient = slackClient;
        this.spotifyClient = spotifyClient;
        this.spotMyStatusProperties = spotMyStatusProperties;
        this.executor = executor;
    }

    @Scheduled(fixedDelay = 1000)
    public void scheduleFixedDelayTask() {
        try {
            var completableFutures = userCache.asMap().values().stream()
                                              .map(cachedUser -> pollUserAsync(cachedUser, userCache.estimatedSize()))
                                              .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(completableFutures).join();
        } catch (CompletionException e) {
            log.error("Caught CompletionException while polling users", e);
        } catch (Exception e) {
            log.error("Failed to poll users", e);
        }
    }

    private CompletableFuture<Void> pollUserAsync(CachedUser cachedUser, long userCount) {
        try {
            sleep(userCount);
            return CompletableFuture.runAsync(() -> pollUser(cachedUser), executor)
                                    .completeOnTimeout(null, spotMyStatusProperties.getTimeout(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Polling user {} timed out", cachedUser.getId());
        }
        return CompletableFuture.completedFuture(null);
    }

    void sleep(long userCount) throws InterruptedException {
        // to mitigate Spotify's rate-limit throttling requests
        Thread.sleep(spotMyStatusProperties.getPollingRate() / userCount);
    }

    private void pollUser(CachedUser cachedUser) {
        try {
            if (slackClient.isUserLive(cachedUser)) {
                updateSlackStatus(cachedUser);
            }
        } catch (Exception e) {
            log.error("Failed to poll user {}", cachedUser.getId(), e);
        }
    }

    private void updateSlackStatus(CachedUser user) {
        spotifyClient.getCurrentLiveTrack(user)
                     .ifPresentOrElse(usersCurrentTrack -> slackClient.updateAndPersistStatus(user, usersCurrentTrack),
                         () -> cleanStatus(user));
    }

    private void cleanStatus(CachedUser user) {
        if (!user.isCleaned()) {
            slackClient.cleanStatus(user);
        }
    }
}
