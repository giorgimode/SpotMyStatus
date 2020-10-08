package com.giorgimode.SpotMyStatus.service;

import com.giorgimode.SpotMyStatus.common.PollingProperties;
import com.giorgimode.SpotMyStatus.model.CachedUser;
import com.giorgimode.SpotMyStatus.model.SpotifyCurrentTrackResponse;
import com.giorgimode.SpotMyStatus.slack.SlackClient;
import com.giorgimode.SpotMyStatus.spotify.SpotifyClient;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StatusUpdateScheduler {

    private static final Random RANDOM = new Random();
    @Autowired
    private LoadingCache<String, CachedUser> userCache;

    @Autowired
    private SlackClient slackClient;

    @Autowired
    private SpotifyClient spotifyClient;

    @Autowired
    private PollingProperties pollingProperties;

    private final ExecutorService service = Executors.newCachedThreadPool();

    @Scheduled(fixedDelayString = "${spotmystatus.polling_rate}")
    public void scheduleFixedDelayTask() {
        try {
            userCache.asMap().values().forEach(cachedUser -> {
                try {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> pollUser(cachedUser), service);
                    future.completeOnTimeout(null, pollingProperties.getTimeout(), TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    log.error("Polling user {} timed out", cachedUser.getId());
                }
            });
        } catch (Exception e) {
            log.error("Failed to poll users", e);
        }
    }

    private void pollUser(CachedUser cachedUser) {
        try {
            if (shouldSlowDownOutsideWorkHours(cachedUser)) {
                log.trace("Skipping the polling for {} outside working hours", cachedUser.getId());
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
            if (cachedUser.isDisabled()) {
                log.trace("Skipping the polling for {} since user account is disabled", cachedUser.getId());
                return;
            }
            updateSlackStatus(cachedUser);
        } catch (Exception e) {
            log.error("Failed to poll user {}", cachedUser.getId(), e);
        }
    }

    private boolean shouldSlowDownOutsideWorkHours(CachedUser user) {
        if (hasBeenPassiveRecently(user) && isNonWorkingHour(user)) {
            log.debug("Slowing down polling outside nonworking hours");
            return RANDOM.nextInt(pollingProperties.getPassivePollingProbability() / 10) != 0;
        }
        return false;
    }

    private boolean isNonWorkingHour(CachedUser user) {
        int currentHour = LocalDateTime.now(ZoneOffset.ofTotalSeconds(user.getTimezoneOffsetSeconds())).getHour();
        return !(currentHour > pollingProperties.getPassivateEndHr() && currentHour < pollingProperties.getPassivateStartHr());
    }

    private boolean hasBeenPassiveRecently(CachedUser user) {
        return user.getUpdatedAt() != null
            && Duration.between(LocalDateTime.now(), user.getUpdatedAt()).toMinutes() > pollingProperties.getPassivateAfterMin();
    }

    private void updateSlackStatus(CachedUser user) {
        spotifyClient.getCurrentTrack(user)
                     .filter(SpotifyCurrentTrackResponse::getIsPlaying)
                     .ifPresentOrElse(usersCurrentTrack -> slackClient.updateAndPersistStatus(user, usersCurrentTrack),
                         () -> cleanStatus(user));
    }

    private void cleanStatus(CachedUser user) {
        if (!user.isCleaned()) {
            slackClient.cleanStatus(user);
        }
    }
}
