package com.giorgimode.SpotMyStatus.slack;

import static com.giorgimode.SpotMyStatus.common.SpotConstants.SLACK_PROFILE_READ_SCOPE;
import static com.giorgimode.SpotMyStatus.common.SpotConstants.SLACK_PROFILE_WRITE_SCOPE;
import static com.giorgimode.SpotMyStatus.common.SpotConstants.SLACK_REDIRECT_PATH;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import com.giorgimode.SpotMyStatus.model.SlackToken;
import com.giorgimode.SpotMyStatus.model.SpotifyCurrentTrackResponse;
import com.giorgimode.SpotMyStatus.persistence.User;
import com.giorgimode.SpotMyStatus.persistence.UserRepository;
import com.giorgimode.SpotMyStatus.util.RestHelper;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Component
@Slf4j
public class SlackClient {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${secret.slack.client_id}")
    private String slackClientId;

    @Value("${secret.slack.client_secret}")
    private String slackClientSecret;

    @Value("${slack_uri}")
    private String slackUri;

    @Autowired
    private UserRepository userRepository;

    public String requestAuthorization() {
        return RestHelper.builder()
                         .withBaseUrl(slackUri + "/oauth/v2/authorize")
                         .withQueryParam("client_id", slackClientId)
                         .withQueryParam("user_scope", SLACK_PROFILE_READ_SCOPE + "," + SLACK_PROFILE_WRITE_SCOPE)
                         .withQueryParam("redirect_uri", "http://localhost:8080" + SLACK_REDIRECT_PATH)
                         .createUri();

    }

    public UUID updateAuthToken(String spotifyCode) {
        SlackToken slackToken = tryCall(() -> RestHelper.builder()
                                                        .withBaseUrl(slackUri + "/api/oauth.v2.access")
                                                        .withQueryParam("client_id", slackClientId)
                                                        .withQueryParam("client_secret", slackClientSecret)
                                                        .withQueryParam("code", spotifyCode)
                                                        .get(restTemplate, SlackToken.class));

        UUID state = UUID.randomUUID();
        persistNewUser(slackToken, state);
        return state;
    }

    private void persistNewUser(SlackToken slackToken, UUID state) {
        User user = new User();
        user.setId(slackToken.getId());
        user.setSlackAccessToken(slackToken.getAccessToken());
        user.setTimezoneOffsetSeconds(getUserTimezone(slackToken));
        user.setState(state);
        userRepository.save(user);
    }

    private Integer getUserTimezone(SlackToken slackToken) {
        String userString = tryCall(() -> RestHelper.builder()
                                                    .withBaseUrl(slackUri + "/api/users.info")
                                                    .withBearer(slackToken.getAccessToken())
                                                    .withContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                                                    .withQueryParam("user", slackToken.getId())
                                                    .get(restTemplate, String.class));

        return JsonPath.read(userString, "$.user.tz_offset");
    }

    public <T> T tryCall(Supplier<ResponseEntity<T>> responseTypeSupplier) {
        ResponseEntity<T> responseEntity;
        try {
            responseEntity = responseTypeSupplier.get();
        } catch (Exception e) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
        if (responseEntity.getBody() == null) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
        return responseEntity.getBody();
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
        RestHelper.builder()
                  .withBaseUrl(slackUri + "/api/users.profile.set")
                  .withBearer(user.getSlackAccessToken())
                  .withContentType(MediaType.APPLICATION_JSON_VALUE)
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
                                                .withBaseUrl(slackUri + "/api/users.getPresence")
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
                                       .withBaseUrl(slackUri + "/api/users.profile.get")
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
