package com.giorgimode.spotmystatus.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SlackResponse {


    private boolean ok;
    private String presence;
    private String error;
    private String statusText;

    @JsonProperty("profile")
    private void unpackNestedProfile(Map<String, Object> profile) {
        if (profile == null) {
            return;
        }
        this.statusText = (String) profile.get("status_text");
    }
}
