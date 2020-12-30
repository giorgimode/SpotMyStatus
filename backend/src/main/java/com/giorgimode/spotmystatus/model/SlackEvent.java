package com.giorgimode.spotmystatus.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SlackEvent {

    private String token;
    private String challenge;
    private String type;
    private Event event;

    public String getEventType() {
        return event == null ? null : event.getType();
    }

    public String getUser() {
        return event == null ? null : event.getUser();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Event {

        private String type;
        private String user;

    }
}
