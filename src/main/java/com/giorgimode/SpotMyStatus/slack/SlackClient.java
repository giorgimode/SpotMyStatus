package com.giorgimode.SpotMyStatus.slack;

import static com.giorgimode.SpotMyStatus.common.SpotConstants.SLACK_PROFILE_READ_SCOPE;
import static com.giorgimode.SpotMyStatus.common.SpotConstants.SLACK_PROFILE_WRITE_SCOPE;
import static com.giorgimode.SpotMyStatus.common.SpotConstants.SLACK_REDIRECT_PATH;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import com.giorgimode.SpotMyStatus.common.PollingProperties;
import com.giorgimode.SpotMyStatus.model.CachedUser;
import com.giorgimode.SpotMyStatus.model.SlackToken;
import com.giorgimode.SpotMyStatus.model.SpotifyCurrentTrackResponse;
import com.giorgimode.SpotMyStatus.persistence.User;
import com.giorgimode.SpotMyStatus.persistence.UserRepository;
import com.giorgimode.SpotMyStatus.service.NotificationService;
import com.giorgimode.SpotMyStatus.util.RestHelper;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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

    @Autowired
    private PollingProperties pollingProperties;

    @Autowired
    private NotificationService cleanupService;

    private static final Random RANDOM = new Random();

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

    public void updateAndPersistStatus(CachedUser user, SpotifyCurrentTrackResponse currentTrack) {
        try {
            tryUpdateAndPersistStatus(user, currentTrack);
        } catch (Exception e) {
            log.error("Failed to update and persist status for user {}", user, e);
        }
    }

    private void tryUpdateAndPersistStatus(CachedUser user, SpotifyCurrentTrackResponse currentTrack) {
        long expiringInMs = currentTrack.getDurationMs() - currentTrack.getProgressMs();
        long expiringOnUnixTime = (System.currentTimeMillis() + expiringInMs) / 1000;
        String newStatus = currentTrack.getArtists() + " - " + currentTrack.getSongTitle();
        SlackStatusPayload statusPayload = new SlackStatusPayload(newStatus, getEmoji(), expiringOnUnixTime);
        if (!newStatus.equalsIgnoreCase(user.getSlackStatus()) || slowDownStatusUpdates()) {
            log.info("Track: \"{}\" expiring in {} seconds", newStatus, expiringInMs / 1000);
            user.setSlackStatus(newStatus);
            updateStatus(user, statusPayload);
        } else {
            log.debug("Track \"{}\" has not changed for user {}", newStatus, user.getId());
        }
        user.setCleaned(false);
        user.setUpdatedAt(LocalDateTime.now());
    }

    private String getEmoji() {
        List<String> emojis = pollingProperties.getEmojis();
        return emojis.get(RANDOM.nextInt(emojis.size()));
    }

    /**
     * if status hasn't changed, there should not be a need to update it again
     * <p>
     * However, Slack sometimes has a hiccup and status is not updated, even though it returns success
     */
    private boolean slowDownStatusUpdates() {
        return RANDOM.nextInt(pollingProperties.getPassivePollingProbability() / 10) == 0;
    }

    private void updateStatus(CachedUser user, SlackStatusPayload statusPayload) {
        ResponseEntity<String> responseEntity = RestHelper.builder()
                                                          .withBaseUrl(slackUri + "/api/users.profile.set")
                                                          .withBearer(user.getSlackAccessToken())
                                                          .withContentType(MediaType.APPLICATION_JSON_VALUE)
                                                          .withBody(statusPayload)
                                                          .post(restTemplate, String.class);
        log.trace("Slack response to status update {}", responseEntity.getBody());
    }

    public void cleanStatus(CachedUser user) {
        log.info("Cleaning status for user {} ", user.getId());
        try {
            SlackStatusPayload statusPayload = new SlackStatusPayload();
            user.setSlackStatus("");
            updateStatus(user, statusPayload);
            user.setCleaned(true);
        } catch (Exception e) {
            log.error("Failed to clean status for user {}", user, e);
        }
    }

    public boolean isUserOffline(CachedUser user) {
        try {
            return checkIsUserOffline(user);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == UNAUTHORIZED || e.getStatusCode() == FORBIDDEN) {
                removeInvalidatedUser(user);
            }
        } catch (Exception e) {
            log.error("Caught", e);
        }
        return true;
    }

    private boolean checkIsUserOffline(CachedUser user) {
        String userPresenceResponse = RestHelper.builder()
                                                .withBaseUrl(slackUri + "/api/users.getPresence")
                                                .withBearer(user.getSlackAccessToken())
                                                .getBody(restTemplate, String.class);

        if (userPresenceResponse.contains("invalid_auth")) {
            removeInvalidatedUser(user);
            return true;
        }
        String usersPresence = JsonPath.read(userPresenceResponse, "$.presence");
        boolean isUserActive = "active".equalsIgnoreCase(usersPresence);
        if (!isUserActive && !user.isCleaned()) {
            log.info("User {} is away.", user.getId());
            cleanStatus(user);
        }
        return !isUserActive;
    }

    private void removeInvalidatedUser(CachedUser user) {
        log.error("User's Slack token has been invalidated. Cleaning up user {}", user.getId());
        cleanupService.invalidateAndNotifyUser(user.getId());
    }

    public boolean statusHasBeenManuallyChanged(CachedUser user) {
        return tryCheck(() -> checkStatusHasBeenChanged(user));
    }

    public boolean checkStatusHasBeenChanged(CachedUser user) {
        String userProfile = RestHelper.builder()
                                       .withBaseUrl(slackUri + "/api/users.profile.get")
                                       .withBearer(user.getSlackAccessToken())
                                       .getBody(restTemplate, String.class);

        String statusText = JsonPath.read(userProfile, "$.profile.status_text");
        boolean statusHasBeenManuallyChanged = isNotBlank(statusText) && !statusText.equalsIgnoreCase(user.getSlackStatus());
        if (statusHasBeenManuallyChanged) {
            log.info("Status for user {} has been manually changed. Skipping the update.", user.getId());
        }
        return statusHasBeenManuallyChanged;
    }

    private boolean tryCheck(Supplier<Boolean> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.error("Caught", e);
        }
        return false;
    }
}
