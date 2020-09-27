package com.giorgimode.SpotMyStatus.service;

import static java.util.function.Predicate.not;
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

    @Scheduled(fixedDelayString = "${slack_polling_rate}")
    public void scheduleFixedDelayTask() {
        userRepository.findAll()
                      .stream()
                      .filter(not(user -> Boolean.TRUE.equals(user.getDisabled())))
                      .forEach(this::updateSlackStatus);

    }

    private void updateSlackStatus(com.giorgimode.SpotMyStatus.persistence.User user) {
        String usersCurrentTrack = spotifyAgent.getCurrentTrack(user.getSpotifyAccessToken());
        slackAgent.updateStatus(user, usersCurrentTrack);
    }
}
