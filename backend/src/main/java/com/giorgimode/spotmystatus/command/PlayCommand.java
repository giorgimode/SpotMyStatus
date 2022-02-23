package com.giorgimode.spotmystatus.command;

import com.giorgimode.spotmystatus.slack.SlackClient;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PlayCommand implements Function<String, String> {

    private final SlackClient slackClient;

    public PlayCommand(SlackClient slackClient) {
        this.slackClient = slackClient;
    }

    @Override
    public String apply(String userId) {
        log.debug("Resuming updates for user {}", userId);
        return slackClient.resume(userId);
    }
}
