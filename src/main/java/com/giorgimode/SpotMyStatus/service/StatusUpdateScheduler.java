package com.giorgimode.SpotMyStatus.service;

import static java.util.function.Predicate.not;
import com.giorgimode.SpotMyStatus.model.SpotifyCurrentTrackResponse;
import com.giorgimode.SpotMyStatus.persistence.User;
import com.giorgimode.SpotMyStatus.persistence.UserRepository;
import com.giorgimode.SpotMyStatus.slack.SlackAgent;
import com.giorgimode.SpotMyStatus.spotify.SpotifyAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StatusUpdateScheduler {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SlackAgent slackAgent;

    @Autowired
    private SpotifyAgent spotifyAgent;

    @Scheduled(fixedDelayString = "${slack.polling_rate}")
    public void scheduleFixedDelayTask() {
        userRepository.findAll()
                      .stream()
                      .filter(not(user -> Boolean.TRUE.equals(user.getDisabled())))
                      .forEach(this::updateSlackStatus);

    }

    private void updateSlackStatus(User user) {
        SpotifyCurrentTrackResponse usersCurrentTrack = spotifyAgent.getCurrentTrack(user.getSpotifyAccessToken());
        if (usersCurrentTrack.isPlaying()) {
            long expiringIn = usersCurrentTrack.getDurationMs() - usersCurrentTrack.getProgressMs();
            slackAgent.updateAndPersistStatus(user, usersCurrentTrack, expiringIn);
        } else {
            slackAgent.cleanStatus(user);
        }
    }
}
