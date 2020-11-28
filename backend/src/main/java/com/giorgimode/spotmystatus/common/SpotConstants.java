package com.giorgimode.spotmystatus.common;

import java.util.List;

public class SpotConstants {

    public static final String SLACK_REDIRECT_PATH = "/slack/redirect";
    public static final String SPOTIFY_REDIRECT_PATH = "/spotify/redirect";
    public static final List<String> SLACK_PROFILE_SCOPES = List.of("users:read", "users.profile:read", "users.profile:write");
    public static final List<String> SLACK_BOT_SCOPES = List.of("chat:write", "commands");
}
