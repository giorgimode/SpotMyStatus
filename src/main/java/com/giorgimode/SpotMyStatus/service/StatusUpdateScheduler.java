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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    //todo convert to bean
    private final ExecutorService service = Executors.newCachedThreadPool();
//    private final ExecutorService service = Executors.newScheduledThreadPool();

    @Scheduled(fixedDelayString = "${spotmystatus.polling_rate}")
    public void scheduleFixedDelayTask() {
        try {
            // https://www.baeldung.com/java-9-reactive-streams
            // https://www.youtube.com/watch?v=_stAxdjx8qk&ab_channel=Devoxx
            // https://www.baeldung.com/rxjava-vs-java-flow-api

            userCache.asMap().values().forEach(cachedUser -> {
                try {
                    service.submit(() -> pollUser(cachedUser)).get(pollingProperties.getTimeout(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException | TimeoutException | ExecutionException e) {
                    log.error("Polling user {} timed out", cachedUser.getId());
                }
            });
        } catch (Exception e) {
            log.error("Failed to poll users", e);
        }
    }

    private void pollUser(CachedUser cachedUser) {
        try {
            //todo refactor
            if (!slowDownIfOutsideWorkHours(cachedUser)) {
                log.trace("Skipping the polling for {} outside working hours", cachedUser.getId());
                return;
            }
            if (!slackClient.isUserOnline(cachedUser)) {
                return;
            }
            if (!slackClient.statusHasNotBeenManuallyChanged(cachedUser)) {
                return;
            }
            if (cachedUser.isDisabled()) {
                return;
            }
            updateSlackStatus(cachedUser);
        } catch (Exception e) {
            log.error("Failed to poll user {}", cachedUser.getId(), e);
        }
    }

    private boolean slowDownIfOutsideWorkHours(CachedUser user) {
        if (hasBeenPassiveRecently(user) && isNonWorkingHour(user)) {
            log.debug("Slowing down polling outside nonworking hours");
            return RANDOM.nextInt(pollingProperties.getPassivePollingProbability() / 10) == 0;
        }
        return true;
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
