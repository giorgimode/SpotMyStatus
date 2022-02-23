package com.giorgimode.spotmystatus.command;

import com.giorgimode.spotmystatus.slack.SlackClient;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PauseCommand implements Function<String, String> {

    private final SlackClient slackClient;

    public PauseCommand(SlackClient slackClient) {
        this.slackClient = slackClient;
    }

    @Override
    public String apply(String userId) {
        log.debug("Pausing updates for user {}", userId);
        return slackClient.pause(userId);
    }
}
