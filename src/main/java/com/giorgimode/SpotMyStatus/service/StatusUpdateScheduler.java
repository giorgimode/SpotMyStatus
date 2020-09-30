package com.giorgimode.SpotMyStatus.service;

import static java.util.function.Predicate.not;
import com.giorgimode.SpotMyStatus.common.PollingProperties;
import com.giorgimode.SpotMyStatus.model.SpotifyCurrentTrackResponse;
import com.giorgimode.SpotMyStatus.persistence.User;
import com.giorgimode.SpotMyStatus.persistence.UserRepository;
import com.giorgimode.SpotMyStatus.slack.SlackClient;
import com.giorgimode.SpotMyStatus.spotify.SpotifyClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StatusUpdateScheduler {

    private static final Random RANDOM = new Random();
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SlackClient slackClient;

    @Autowired
    private SpotifyClient spotifyClient;

    @Autowired
    private PollingProperties pollingProperties;

    @Scheduled(fixedDelayString = "${spotmystatus.polling_rate}")
    public void scheduleFixedDelayTask() {
        try {
            userRepository.findAll()
                          .stream()
                          .filter(this::slowDownIfOutsideWorkHours)
                          .filter(user -> slackClient.isUserOnline(user))
                          .filter(user -> slackClient.statusHasNotBeenManuallyChanged(user))
                          .filter(not(user -> Boolean.TRUE.equals(user.getDisabled())))
                          .forEach(this::updateSlackStatus);
        } catch (Exception e) {
            log.error("Failed to poll users", e);
        }
    }

    private boolean slowDownIfOutsideWorkHours(User user) {
        if (hasBeenPassiveRecently(user) && isNonWorkingHour(user)) {
            log.debug("Slowing down polling outside nonworking hours");
            return RANDOM.nextInt(pollingProperties.getPassivePollingProbability() / 10) == 0;
        }
        return true;
    }

    private boolean isNonWorkingHour(User user) {
        int currentHour = LocalDateTime.now(ZoneOffset.ofTotalSeconds(user.getTimezoneOffsetSeconds())).getHour();
        return !(currentHour > pollingProperties.getPassivateEndHr() && currentHour < pollingProperties.getPassivateStartHr());
    }

    private boolean hasBeenPassiveRecently(User user) {
        return user.getUpdatedAt() != null
            && Duration.between(LocalDateTime.now(), user.getUpdatedAt()).toMinutes() > pollingProperties.getPassivateAfterMin();
    }

    private void updateSlackStatus(User user) {
        spotifyClient.getCurrentTrack(user.getSpotifyAccessToken())
                     .filter(SpotifyCurrentTrackResponse::getIsPlaying)
                     .ifPresentOrElse(usersCurrentTrack -> slackClient.updateAndPersistStatus(user, usersCurrentTrack),
                         () -> slackClient.cleanStatus(user));
    }
}
