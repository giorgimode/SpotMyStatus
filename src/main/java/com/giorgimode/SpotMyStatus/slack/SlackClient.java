package com.giorgimode.SpotMyStatus.slack;

import static com.giorgimode.SpotMyStatus.common.SpotConstants.SLACK_PROFILE_READ_SCOPE;
import static com.giorgimode.SpotMyStatus.common.SpotConstants.SLACK_PROFILE_WRITE_SCOPE;
import static com.giorgimode.SpotMyStatus.common.SpotConstants.SLACK_REDIRECT_PATH;
import static com.giorgimode.SpotMyStatus.util.SpotUtil.baseUri;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import com.giorgimode.SpotMyStatus.common.PollingProperties;
import com.giorgimode.SpotMyStatus.common.PropertyVault;
import com.giorgimode.SpotMyStatus.model.CachedUser;
import com.giorgimode.SpotMyStatus.model.SlackToken;
import com.giorgimode.SpotMyStatus.model.SpotifyCurrentTrackResponse;
import com.giorgimode.SpotMyStatus.persistence.User;
import com.giorgimode.SpotMyStatus.persistence.UserRepository;
import com.giorgimode.SpotMyStatus.service.NotificationService;
import com.giorgimode.SpotMyStatus.util.RestHelper;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Component
@Slf4j
public class SlackClient {

    private static final Random RANDOM = new Random();
    private static final String SHA_256_ALGORITHM = "HmacSHA256";

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

    @Autowired
    private LoadingCache<String, CachedUser> userCache;

    @Autowired
    private PropertyVault propertyVault;

    public String requestAuthorization() {
        return RestHelper.builder()
                         .withBaseUrl(slackUri + "/oauth/v2/authorize")
                         .withQueryParam("client_id", slackClientId)
                         .withQueryParam("user_scope", SLACK_PROFILE_READ_SCOPE + "," + SLACK_PROFILE_WRITE_SCOPE)
                         .withQueryParam("redirect_uri", baseUri()  + SLACK_REDIRECT_PATH)
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
        String newStatus = buildNewStatus(currentTrack);
        SlackStatusPayload statusPayload = new SlackStatusPayload(newStatus, getEmoji(), expiringOnUnixTime);
        if (!newStatus.equalsIgnoreCase(user.getSlackStatus())) {
            if (updateStatus(user, statusPayload)) {
                log.info("Track: \"{}\" expiring in {} seconds", newStatus, expiringInMs / 1000);
                user.setSlackStatus(newStatus);
            }
        } else {
            log.debug("Track \"{}\" has not changed for user {}", newStatus, user.getId());
        }
        user.setCleaned(false);
        user.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Slack only allows max 100character as a status. In this case we first drop extra artists and also trim if necessary
     */
    private String buildNewStatus(SpotifyCurrentTrackResponse currentTrack) {
        String newStatus = String.join(", ", currentTrack.getArtists()) + " - " + currentTrack.getSongTitle();
        if (newStatus.length() > 100) {
            String firstArtistOnly = currentTrack.getArtists().get(0);
            newStatus = StringUtils.abbreviate(firstArtistOnly + " - " + currentTrack.getSongTitle(), 100);
        }
        return newStatus;
    }

    private String getEmoji() {
        List<String> emojis = pollingProperties.getEmojis();
        return emojis.get(RANDOM.nextInt(emojis.size()));
    }

    private boolean updateStatus(CachedUser user, SlackStatusPayload statusPayload) {
        //noinspection deprecation: Slack issues warning on missing charset
        String response = RestHelper.builder()
                                    .withBaseUrl(slackUri + "/api/users.profile.set")
                                    .withBearer(user.getSlackAccessToken())
                                    .withContentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                                    .withBody(statusPayload)
                                    .postAndGetBody(restTemplate, String.class);

        log.trace("Slack response to status update {}", response);
        return JsonPath.read(response, "$.ok");
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
        // Slack escapes reserved characters, see here https://api.slack.com/reference/surfaces/formatting#escaping
        String sanitizedStatus = statusText.replaceAll("&amp;", "&")
                                           .replaceAll("&lt;", "<")
                                           .replaceAll("&gt;", ">");
        boolean statusHasBeenManuallyChanged = isNotBlank(sanitizedStatus) &&
            (!sanitizedStatus.equalsIgnoreCase(user.getSlackStatus()) || user.isManualStatus());
        if (statusHasBeenManuallyChanged) {
            log.info("Status for user {} has been manually changed. Skipping the update.", user.getId());
            user.setManualStatus(true);
        } else {
            user.setManualStatus(false);
        }
        user.setSlackStatus(sanitizedStatus);
        return statusHasBeenManuallyChanged;
    }


    public String pause(String userId) {
        return Optional.ofNullable(userCache.getIfPresent(userId))
                       .map(cachedUser -> {
                           cleanStatus(cachedUser);
                           cachedUser.setDisabled(true); //todo persist
                           return "Status updates have been paused";
                       })
                       .orElse("User not found");
    }

    public String resume(String userId) {
        return Optional.ofNullable(userCache.getIfPresent(userId))
                       .map(cachedUser -> {
                           cachedUser.setDisabled(false);
                           return "Status updates have been resumed";
                       })
                       .orElse("User not found");
    }

    public void validateSignature(Long timestamp, String signature, String bodyString) {
        calculateSha256("v0:" + timestamp + ":" + bodyString)
            .filter(hashedString -> ("v0=" + hashedString).equalsIgnoreCase(signature))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }

    private Optional<String> calculateSha256(String message) {
        try {
            Mac mac = Mac.getInstance(SHA_256_ALGORITHM);
            mac.init(new SecretKeySpec(propertyVault.getSlack().getSigningSecret().getBytes(UTF_8), SHA_256_ALGORITHM));
            return Optional.of(DatatypeConverter.printHexBinary(mac.doFinal(message.getBytes(UTF_8))));
        } catch (Exception e) {
            log.error("Failed to calculate hmac-sha256", e);
            return Optional.empty();
        }
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
