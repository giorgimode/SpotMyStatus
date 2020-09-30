package com.giorgimode.SpotMyStatus.slack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SlackStatusPayload {

    private StatusPayload profile;

    public SlackStatusPayload(String statusText, String statusEmoji, Long statusExpiration) {
        StatusPayload payload = new StatusPayload();
        payload.setStatusText(statusText);
        payload.setStatusEmoji(statusEmoji);
        payload.setStatusExpiration(statusExpiration);
        this.profile = payload;
    }

    public SlackStatusPayload(String statusText, String statusEmoji) {
        this(statusText, statusEmoji, null);
    }

    @Getter
    @Setter
    public static class StatusPayload {

        @JsonProperty("status_text")
        private String statusText;


        @JsonProperty("status_emoji")
        private String statusEmoji;

        @JsonProperty("status_expiration")
        private Long statusExpiration;
    }


}
