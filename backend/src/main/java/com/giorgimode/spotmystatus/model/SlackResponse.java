package com.giorgimode.spotmystatus.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SlackResponse {

    private String presence;
    private String error;
    private String statusText;
    private Integer timezoneOffset;

    @JsonProperty("user")
    private void unpackNestedUser(Map<String, Object> user) {
        if (user == null) {
            return;
        }
        this.timezoneOffset = (Integer) user.get("tz_offset");
    }
}
