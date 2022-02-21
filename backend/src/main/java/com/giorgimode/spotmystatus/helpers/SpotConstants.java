package com.giorgimode.spotmystatus.helpers;

import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SpotConstants {

    public static final String PAYLOAD_TYPE_BLOCK_ACTIONS = "block_actions";
    public static final String ACTION_START_HOUR = "start_hour-action";
    public static final String ACTION_END_HOUR = "end_hour-action";
    public static final String PAYLOAD_TYPE_SUBMISSION = "view_submission";
    public static final String BLOCK_ID_EMOJI_LIST = "emoji_list_block";
    public static final String BLOCK_ID_EMOJI_INPUT = "emoji_input_block";
    public static final String BLOCK_ID_SYNC_TOGGLE = "sync_toggle_block";
    public static final String BLOCK_ID_SPOTIFY_ITEMS = "spotify_items_block";
    public static final String BLOCK_ID_HOURS_INPUT = "hours_input_block";
    public static final String BLOCK_ID_SPOTIFY_DEVICES = "spotify_devices_block";
    public static final String BLOCK_ID_PURGE = "purge_block";
    public static final String BLOCK_ID_INVALID_HOURS = "invalid_hours_block";
    public static final String BLOCK_ID_INVALID_EMOJI = "invalid_emoji_block";
    public static final String BLOCK_ID_APP_URI = "spotmystatus_uri_block";
    public static final String BLOCK_ID_SUBMIT = "submit_block";
    public static final String BLOCK_ID_FIRST_DIVIDER = "first_divider_block";
    public static final String ALL_DEVICES_OFFLINE_VALUE = "all_devices_offline";
    public static final String ALL_DEVICES_ALLOWED_VALUE = "all_devices_allowed";
    public static final String ALL_DEVICES_ALLOWED_TEXT = "Any Device";
    public static final String EMOJI_REGEX = "^[a-z0-9-_]+$";
    public static final String SLACK_REDIRECT_PATH = "/slack/redirect";
    public static final String SPOTIFY_REDIRECT_PATH = "/spotify/redirect";
    public static final String SPOTIFY_SCOPE_USER_PLAYBACK = "user-read-playback-state";
    public static final List<String> SLACK_PROFILE_SCOPES = List.of("users:read", "users.profile:read", "users.profile:write");
    public static final List<String> SLACK_BOT_SCOPES = List.of("chat:write", "commands");
    public static final String MODAL_FOOTER_MESSAGE = ":house: <%1$s|_*SpotMyStatus Home*_> | :male-mechanic: <%1$s/support|_*SpotMyStatus Support*_>";
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");
}
