package com.giorgimode.spotmystatus.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SlackToken {

    @JsonProperty("access_token")
    private String botToken;

    @JsonProperty("authed_user")
    private SlackTokenPayload authUser;

    public String getScope() {
        return Optional.ofNullable(authUser).map(SlackTokenPayload::getScope).orElse(null);
    }

    public String getAccessToken() {
        return Optional.ofNullable(authUser).map(SlackTokenPayload::getAccessToken).orElse(null);
    }

    public String getId() {
        return Optional.ofNullable(authUser).map(SlackTokenPayload::getId).orElse(null);
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SlackTokenPayload {

        private String id;

        private String scope;

        @JsonProperty("access_token")
        private String accessToken;
    }
}
