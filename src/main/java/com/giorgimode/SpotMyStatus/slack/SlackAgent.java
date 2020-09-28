package com.giorgimode.SpotMyStatus.slack;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import com.giorgimode.SpotMyStatus.model.SlackToken;
import com.giorgimode.SpotMyStatus.model.SpotifyCurrentTrackResponse;
import com.giorgimode.SpotMyStatus.persistence.User;
import com.giorgimode.SpotMyStatus.persistence.UserRepository;
import com.giorgimode.SpotMyStatus.util.RestHelper;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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

        String userString = RestHelper.builder()
                                      .withBaseUrl("https://slack.com/api/users.info")
                                      .withBearer(slackToken.getAccessToken())
                                      .withContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                                      .withQueryParam("user", slackToken.getId())
                                      .getBody(restTemplate, String.class);

        log.debug(userString);
        Integer timezoneOffsetInSeconds = JsonPath.read(userString, "$.user.tz_offset");

        User user = new User();
        user.setId(slackToken.getId());
        user.setSlackAccessToken(slackToken.getAccessToken());
        UUID state = UUID.randomUUID();
        user.setTimezoneOffsetSeconds(timezoneOffsetInSeconds);
        user.setState(state);
        userRepository.save(user);
        return state;
    }

    public void updateAndPersistStatus(User user, SpotifyCurrentTrackResponse currentTrack) {
        long expiringIn = currentTrack.getDurationMs() - currentTrack.getProgressMs();
        String newSlackStatus = currentTrack.getArtists() + " - " + currentTrack.getSongTitle();
        SlackStatusPayload statusPayload = new SlackStatusPayload(newSlackStatus, ":headphones:", expiringIn);
        // todo fails to update if user manually changes the status and then cleans it
        if (!newSlackStatus.equalsIgnoreCase(user.getSlackStatus())) {
            log.info("Track: {} expiring in {}", newSlackStatus, expiringIn);
            user.setSlackStatus(newSlackStatus);
            updateStatus(user, statusPayload);
        }
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

    public boolean isUserOnline(User user) {
        String userPresenceResponse = RestHelper.builder()
                                                .withBaseUrl("https://slack.com/api/users.getPresence")
                                                .withBearer(user.getSlackAccessToken())
                                                .getBody(restTemplate, String.class);

        String usersPresence = JsonPath.read(userPresenceResponse, "$.presence");
        boolean isUserActive = "active".equalsIgnoreCase(usersPresence);
//        if (!isUserActive) { todo clean only once
//            log.info("User {} is away.", user.getId());
//            cleanStatus(user);
//        }
        return isUserActive;
    }

    public boolean statusHasNotBeenManuallyChanged(User user) {
        String userProfile = RestHelper.builder()
                                       .withBaseUrl("https://slack.com/api/users.profile.get")
                                       .withBearer(user.getSlackAccessToken())
                                       .getBody(restTemplate, String.class);

        String statusText = JsonPath.read(userProfile, "$.profile.status_text");
        boolean statusHasNotBeenManuallyChanged = isBlank(statusText) || isNotBlank(user.getSlackStatus()) &&
            user.getSlackStatus().equalsIgnoreCase(statusText);
        if (!statusHasNotBeenManuallyChanged) {
            log.info("Status for user {} has been manually changed. Skipping the update.", user.getId());
        }
        return statusHasNotBeenManuallyChanged;
    }
}
