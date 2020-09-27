package com.giorgimode.SpotMyStatus.slack;

import com.giorgimode.SpotMyStatus.spotify.SpotifyAgent;
import com.giorgimode.SpotMyStatus.util.RestHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class SlackAgent {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${secret.slack.client_id}")
    private String slackClientId;

    @Value("${secret.slack.client_secret}")
    private String slackClientSecret;

    //todo remove this monstrosity
    private SlackToken slackToken;

    @Autowired
    private SpotifyAgent spotifyAgent;

    public String requestAuthorization() {
        return RestHelper.builder()
                         .withBaseUrl("https://slack.com/oauth/v2/authorize")
                         .withQueryParam("client_id", slackClientId)
                         .withQueryParam("user_scope", "users.profile:read,users.profile:write")
                         .withQueryParam("redirect_uri", "http://localhost:8080/redirect2")
                         .createUri();

    }

    public void updateAuthToken(String spotifyCode) {
        SlackToken slackToken = RestHelper.builder()
                                          .withBaseUrl("https://slack.com/api/oauth.v2.access")
                                          .withQueryParam("client_id", slackClientId)
                                          .withQueryParam("client_secret", slackClientSecret)
                                          .withQueryParam("code", spotifyCode)
                                          .get(restTemplate, SlackToken.class)
                                          .getBody();
        // email
        // slack access code
        // state
        // spotify access token
        // spotify refresh token
        // status

        this.slackToken = slackToken;  //persist to db
    }

    public String updateStatus() {
        SlackStatusPayload statusPayload = new SlackStatusPayload(spotifyAgent.getCurrentTrack(), ":headphones:");
        String response = RestHelper.builder()
                                    .withBaseUrl("https://slack.com/api/users.profile.set")
                                    .withBearer(slackToken.getAccessToken())
                                    .withContentType("application/json; charset=utf-8")
                                    .withBody(statusPayload)
                                    .post(restTemplate, String.class)
                                    .getBody();

        return response;
    }
}
