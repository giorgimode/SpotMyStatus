package com.giorgimode.SpotMyStatus.slack;

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

    @Getter
    @Setter
    public class SlackTokenPayload {

        private String scope;

        @JsonProperty("access_token")
        private String accessToken;
    }
}
