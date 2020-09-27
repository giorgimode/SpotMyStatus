package com.giorgimode.SpotMyStatus.service;

import static java.util.function.Predicate.not;
import com.giorgimode.SpotMyStatus.persistence.UserRepository;
import com.giorgimode.SpotMyStatus.slack.SlackAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StatusUpdateScheduler {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SlackAgent slackAgent;

    @Scheduled(fixedDelayString = "${slack_polling_rate}")
    public void scheduleFixedDelayTask() {
        userRepository.findAll()
                      .stream()
                      .filter(not(user -> Boolean.TRUE.equals(user.getDisabled())))
                      .forEach(user -> slackAgent.updateStatus(user));

    }
}
