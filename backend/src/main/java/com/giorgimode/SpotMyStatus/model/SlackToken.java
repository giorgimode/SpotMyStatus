package com.giorgimode.SpotMyStatus.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class SlackToken {

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
    public static class SlackTokenPayload {

        private String id;

        private String scope;

        @JsonProperty("access_token")
        private String accessToken;
    }
}
