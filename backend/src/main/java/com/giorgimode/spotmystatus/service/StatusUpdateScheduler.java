package com.giorgimode.spotmystatus.service;

import com.giorgimode.spotmystatus.helpers.SpotMyStatusProperties;
import com.giorgimode.spotmystatus.model.CachedUser;
import com.giorgimode.spotmystatus.model.SpotifyCurrentItem;
import com.giorgimode.spotmystatus.model.SpotifyItem;
import com.giorgimode.spotmystatus.slack.SlackClient;
import com.giorgimode.spotmystatus.spotify.SpotifyClient;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.index.qual.NonNegative;
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
            userCache.asMap().values().forEach(cachedUser -> pollUserAsync(cachedUser, userCache.estimatedSize()));
        } catch (Exception e) {
            log.error("Failed to poll users", e);
        }
    }

    private void pollUserAsync(CachedUser cachedUser, long userCount) {
        try {
            sleep(userCount);
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> pollUser(cachedUser), executor);
            future.completeOnTimeout(null, spotMyStatusProperties.getTimeout(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Polling user {} timed out", cachedUser.getId());
        }
    }

    void sleep(long userCount) throws InterruptedException {
        // to mitigate Spotify's rate-limit throttling requests
        Thread.sleep(spotMyStatusProperties.getPollingRate() / userCount);
    }

    private void pollUser(CachedUser cachedUser) {
        try {
            if (cachedUser.isDisabled()) {
                log.trace("Skipping the polling for {} since user account is disabled", cachedUser.getId());
                return;
            }

            if (slackClient.isUserOffline(cachedUser)) {
                log.trace("Skipping the polling for {} since user is offline", cachedUser.getId());
                return;
            }
            if (slackClient.statusHasBeenManuallyChanged(cachedUser)) {
                log.trace("Skipping the polling for {} since status has been manually updated", cachedUser.getId());
                return;
            }
            updateSlackStatus(cachedUser);
        } catch (Exception e) {
            log.error("Failed to poll user {}", cachedUser.getId(), e);
        }
    }


    private void updateSlackStatus(CachedUser user) {
        spotifyClient.getCurrentTrack(user)
                     .filter(SpotifyCurrentItem::getIsPlaying)
                     .filter(spotifyItem -> isPlayingDeviceEnabled(user, spotifyItem))
                     .filter(spotifyItem -> isItemEnabled(user, spotifyItem))
                     .ifPresentOrElse(usersCurrentTrack -> slackClient.updateAndPersistStatus(user, usersCurrentTrack),
                         () -> cleanStatus(user));
    }

    private boolean isPlayingDeviceEnabled(CachedUser user, SpotifyCurrentItem spotifyCurrentItem) {
        boolean isCurrentDeviceEnabled = user.getSpotifyDeviceIds().isEmpty()
            || user.getSpotifyDeviceIds().contains(spotifyCurrentItem.getDevice().getId());
        if (!isCurrentDeviceEnabled) {
            log.debug("Skipping syncing, since spotify device is not enabled for user {}", user.getId());
        }
        return isCurrentDeviceEnabled;
    }

    private boolean isItemEnabled(CachedUser user, SpotifyCurrentItem currentItem) {
        boolean isItemEnabled = user.getSpotifyItems().isEmpty() || user.getSpotifyItems().contains(SpotifyItem.from(currentItem.getType()));
        if (!isItemEnabled) {
            log.debug("Skipping syncing, since spotify item type {} is not enabled for user {}", currentItem.getType(), user.getId());
        }
        return isItemEnabled;
    }

    private void cleanStatus(CachedUser user) {
        if (!user.isCleaned()) {
            slackClient.cleanStatus(user);
        }
    }
}
