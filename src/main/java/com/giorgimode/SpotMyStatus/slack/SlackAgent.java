package com.giorgimode.SpotMyStatus.slack;

import com.giorgimode.SpotMyStatus.model.SlackToken;
import com.giorgimode.SpotMyStatus.model.SpotifyCurrentTrackResponse;
import com.giorgimode.SpotMyStatus.persistence.User;
import com.giorgimode.SpotMyStatus.persistence.UserRepository;
import com.giorgimode.SpotMyStatus.util.RestHelper;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class SlackAgent {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${secret.slack.client_id}")
    private String slackClientId;

    @Value("${secret.slack.client_secret}")
    private String slackClientSecret;

    @Autowired
    private UserRepository userRepository;

    public String requestAuthorization() {
        return RestHelper.builder()
                         .withBaseUrl("https://slack.com/oauth/v2/authorize")
                         .withQueryParam("client_id", slackClientId)
                         .withQueryParam("user_scope", "users.profile:read,users.profile:write")
                         .withQueryParam("redirect_uri", "http://localhost:8080/redirect2")
                         .createUri();

    }

    public UUID updateAuthToken(String spotifyCode) {
        SlackToken slackToken = RestHelper.builder()
                                          .withBaseUrl("https://slack.com/api/oauth.v2.access")
                                          .withQueryParam("client_id", slackClientId)
                                          .withQueryParam("client_secret", slackClientSecret)
                                          .withQueryParam("code", spotifyCode)
                                          .get(restTemplate, SlackToken.class)
                                          .getBody();

        User user = new User();
        user.setId(slackToken.getId());
        user.setSlackAccessToken(slackToken.getAccessToken());
        UUID state = UUID.randomUUID();
        user.setState(state);
        userRepository.save(user);
        return state;
    }

    public void updateAndPersistStatus(User user, SpotifyCurrentTrackResponse currentTrack, Long expiringIn) {
        String slackStatus = currentTrack.getArtists() + " - " + currentTrack.getSongTitle();
        log.info("Track: {} expiring in {}", slackStatus, expiringIn);
        SlackStatusPayload statusPayload = new SlackStatusPayload(slackStatus, ":headphones:", expiringIn);
        updateStatus(user, statusPayload);

        user.setSlackStatus(slackStatus);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    private void updateStatus(User user, SlackStatusPayload statusPayload) {
        ResponseEntity<String> responseEntity = RestHelper.builder()
                                                          .withBaseUrl("https://slack.com/api/users.profile.set")
                                                          .withBearer(user.getSlackAccessToken())
                                                          .withContentType("application/json; charset=utf-8")
                                                          .withBody(statusPayload)
                                                          .post(restTemplate, String.class);
    }

    public void cleanStatus(User user) {
        log.info("Cleaning status for user {} ", user.getId());
        SlackStatusPayload statusPayload = new SlackStatusPayload("", "");
        updateStatus(user, statusPayload);
    }

}
