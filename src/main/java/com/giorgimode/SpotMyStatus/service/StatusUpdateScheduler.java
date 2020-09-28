package com.giorgimode.SpotMyStatus.service;

import static java.util.function.Predicate.not;
import com.giorgimode.SpotMyStatus.beans.PollingProperties;
import com.giorgimode.SpotMyStatus.model.SpotifyCurrentTrackResponse;
import com.giorgimode.SpotMyStatus.persistence.User;
import com.giorgimode.SpotMyStatus.persistence.UserRepository;
import com.giorgimode.SpotMyStatus.slack.SlackAgent;
import com.giorgimode.SpotMyStatus.spotify.SpotifyAgent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StatusUpdateScheduler {

    private static final Random RANDOM = new Random();
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SlackAgent slackAgent;

    @Autowired
    private SpotifyAgent spotifyAgent;

    @Autowired
    private PollingProperties pollingProperties;

    //todo
    //  1. get presence - online 2. get slack status - not same as current one, set new one
    //todo threshhold if too many failures, remove the user tokens, send message to the user in slack
    @Scheduled(fixedDelayString = "${spotmystatus.polling_rate}")
    public void scheduleFixedDelayTask() {
        userRepository.findAll()
                      .stream()
                      .filter(this::slowDownIfOutsideWorkHours)
                      //    .filter(this::isOnlineOnSlack) - get presence
                      // filter
                      .filter(not(user -> Boolean.TRUE.equals(user.getDisabled())))
                      .forEach(this::updateSlackStatus);

    }

    private boolean slowDownIfOutsideWorkHours(User user) {
        if (hasBeenPassiveRecently(user) && isNonWorkingHour(user)) {
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
        SpotifyCurrentTrackResponse usersCurrentTrack = spotifyAgent.getCurrentTrack(user.getSpotifyAccessToken());
        if (usersCurrentTrack.isPlaying()) {
            slackAgent.updateAndPersistStatus(user, usersCurrentTrack);
        } else {
            slackAgent.cleanStatus(user);
        }
    }
}
