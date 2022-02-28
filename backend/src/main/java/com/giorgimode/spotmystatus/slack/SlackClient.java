package com.giorgimode.spotmystatus.slack;

import static com.giorgimode.spotmystatus.helpers.SpotConstants.SLACK_BOT_SCOPES;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.SLACK_PROFILE_SCOPES;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.SLACK_REDIRECT_PATH;
import static com.giorgimode.spotmystatus.helpers.SpotUtil.baseUri;
import static com.giorgimode.spotmystatus.model.SpotifyItem.EPISODE;
import static java.util.function.Predicate.not;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import com.giorgimode.spotmystatus.exceptions.UserNotFoundException;
import com.giorgimode.spotmystatus.helpers.PropertyVault;
import com.giorgimode.spotmystatus.helpers.RestHelper;
import com.giorgimode.spotmystatus.helpers.SpotMyStatusProperties;
import com.giorgimode.spotmystatus.model.CachedUser;
import com.giorgimode.spotmystatus.model.SlackMessage;
import com.giorgimode.spotmystatus.model.SlackResponse;
import com.giorgimode.spotmystatus.model.SlackToken;
import com.giorgimode.spotmystatus.model.SpotifyCurrentItem;
import com.giorgimode.spotmystatus.persistence.User;
import com.giorgimode.spotmystatus.persistence.UserRepository;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
    private static final String MISSING_USER_ERROR = "User not found";
    private static final String SPOTIFY_INVALIDATED_MESSAGE = "Spotify token has been invalidated. Please authorize again";

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final SpotMyStatusProperties configProperties;
    private final LoadingCache<String, CachedUser> userCache;
    private final PropertyVault propertyVault;

    public SlackClient(RestTemplate restTemplate, UserRepository userRepository,
        SpotMyStatusProperties configProperties, LoadingCache<String, CachedUser> userCache,
        PropertyVault propertyVault) {

        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
        this.configProperties = configProperties;
        this.userCache = userCache;
        this.propertyVault = propertyVault;
    }

    public String requestAuthorization() {
        return RestHelper.builder()
                         .withBaseUrl(configProperties.getSlackUri() + "/oauth/v2/authorize")
                         .withQueryParam("client_id", propertyVault.getSlack().getClientId())
                         .withQueryParam("user_scope", String.join(",", SLACK_PROFILE_SCOPES))
                         .withQueryParam("scope", String.join(",", SLACK_BOT_SCOPES))
                         .withQueryParam("redirect_uri", getRedirectUri())
                         .createUri();

    }

    public UUID updateAuthToken(String slackCode) {
        SlackToken slackToken = tryCall(() -> RestHelper.builder()
                                                        .withBaseUrl(configProperties.getSlackUri() + "/api/oauth.v2.access")
                                                        .withQueryParam("client_id", propertyVault.getSlack().getClientId())
                                                        .withQueryParam("client_secret", propertyVault.getSlack().getClientSecret())
                                                        .withQueryParam("code", slackCode)
                                                        .withQueryParam("redirect_uri", getRedirectUri())
                                                        .get(restTemplate, SlackToken.class));
        if (isBlank(slackToken.getAccessToken()) || isBlank(slackToken.getBotToken())) {
            log.error("Slack access token not returned");
            throw new ResponseStatusException(UNAUTHORIZED);
        }
        UUID state = UUID.randomUUID();
        persistNewUser(slackToken, state);
        return state;
    }

    private String getRedirectUri() {
        return baseUri(configProperties.getRedirectUriScheme()) + "/api" + SLACK_REDIRECT_PATH;
    }

    private void persistNewUser(SlackToken slackToken, UUID state) {
        User user = new User();
        user.setId(slackToken.getId());
        user.setTeamId(slackToken.getTeamId());
        user.setSlackAccessToken(slackToken.getAccessToken());
        user.setSlackBotToken(slackToken.getBotToken());
        user.setTimezoneOffsetSeconds(getUserTimezone(slackToken));
        user.setState(state);
        userRepository.save(user);
    }

    private Integer getUserTimezone(SlackToken slackToken) {
        SlackResponse response = tryCall(() -> RestHelper.builder()
                                                         .withBaseUrl(configProperties.getSlackUri() + "/api/users.info")
                                                         .withBearer(slackToken.getAccessToken())
                                                         .withContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                                                         .withQueryParam("user", slackToken.getId())
                                                         .get(restTemplate, SlackResponse.class));

        log.trace("Received response {}", response);
        return response.getTimezoneOffset();
    }

    private <T> T tryCall(Supplier<ResponseEntity<T>> responseTypeSupplier) {
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

    public void updateAndPersistStatus(CachedUser user, SpotifyCurrentItem currentTrack) {
        try {
            tryUpdateAndPersistStatus(user, currentTrack);
        } catch (Exception e) {
            log.error("Failed to update and persist status for user {}", user, e);
        }
    }

    private void tryUpdateAndPersistStatus(CachedUser user, SpotifyCurrentItem currentTrack) {
        long expiringInMs = currentTrack.getDurationMs() - currentTrack.getProgressMs() + configProperties.getExpirationOverhead();
        long expiringOnUnixTime = (System.currentTimeMillis() + expiringInMs) / 1000;
        // Slack only allows max 100character as a status
        String newStatus = currentTrack.generateFullTitle(100);
        SlackStatusPayload statusPayload = new SlackStatusPayload(newStatus, getEmoji(currentTrack, user), expiringOnUnixTime);
        if (!newStatus.equalsIgnoreCase(user.getSlackStatus())) {
            if (updateStatus(user, statusPayload)) {
                log.debug("Track: \"{}\" expiring in {} seconds", newStatus, expiringInMs / 1000);
                user.setSlackStatus(newStatus);
            }
        } else {
            log.debug("Track \"{}\" has not changed for user {}, expiring in {} seconds", newStatus, user.getId(), expiringInMs / 1000);
        }
        user.setCleaned(false);
        user.setUpdatedAt(LocalDateTime.now());
    }

    private String getEmoji(SpotifyCurrentItem currentTrack, CachedUser user) {
        if (EPISODE.title().equals(currentTrack.getType())) {
            return ":" + configProperties.getPodcastEmoji() + ":";
        }

        if (user.getEmojis().isEmpty()) {
            List<String> defaultEmojis = configProperties.getDefaultEmojis();
            return String.format(":%s:", defaultEmojis.get(RANDOM.nextInt(defaultEmojis.size())));
        } else if (user.getEmojis().size() == 1) {
            return String.format(":%s:", user.getEmojis().get(0));
        } else {
            String currentEmoji = Optional.ofNullable(user.getCurrentEmoji())
                                          .filter(StringUtils::isNotBlank)
                                          .map(emoji -> emoji.replace(":", ""))
                                          .orElse(null);
            List<String> emojis = user.getEmojis().stream()
                                      .filter(emoji -> !emoji.equalsIgnoreCase(currentEmoji))
                                      .collect(Collectors.toList());
            return String.format(":%s:", emojis.get(RANDOM.nextInt(emojis.size())));
        }
    }

    private boolean updateStatus(CachedUser cachedUser, SlackStatusPayload requestStatusPayload) {
        //noinspection deprecation: Slack issues warning on missing charset
        SlackStatusPayload responseStatusPayload = RestHelper.builder()
                                                             .withBaseUrl(configProperties.getSlackUri() + "/api/users.profile.set")
                                                             .withBearer(cachedUser.getSlackAccessToken())
                                                             .withContentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                                                             .withBody(requestStatusPayload)
                                                             .postAndGetBody(restTemplate, SlackStatusPayload.class);

        log.trace("Slack response to status update {}", responseStatusPayload);
        validateSlackResult(cachedUser, requestStatusPayload, responseStatusPayload);
        return responseStatusPayload.isOk();
    }

    private void validateSlackResult(CachedUser cachedUser, SlackStatusPayload statusPayload, SlackStatusPayload response) {
        if ("profile_status_set_failed_not_valid_emoji".equals(response.getError())) {
            String emojiToRemove = statusPayload.getProfile().getStatusEmoji().replace(":", "");
            log.warn("Removing invalid emoji {} from user {}", emojiToRemove, cachedUser.getId());
            List<String> validEmojis = cachedUser.getEmojis().stream().filter(not(emojiToRemove::equals)).collect(Collectors.toList());
            cachedUser.setEmojis(validEmojis);
            userRepository.findById(cachedUser.getId()).ifPresent(user -> {
                user.setEmojis(trimToNull(String.join(",", cachedUser.getEmojis())));
                userRepository.save(user);
            });
        }
    }

    public void cleanStatus(CachedUser user) {
        if (user.isManualStatus()) {
            return;
        }
        log.debug("Cleaning status for user {} ", user.getId());
        try {
            SlackStatusPayload statusPayload = new SlackStatusPayload();
            user.setSlackStatus("");
            updateStatus(user, statusPayload);
            user.setCleaned(true);
        } catch (Exception e) {
            log.error("Failed to clean status for user {}", user, e);
        }
    }

    private boolean isUserOffline(CachedUser user) {
        try {
            return checkIsUserOffline(user);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == UNAUTHORIZED || e.getStatusCode() == FORBIDDEN) {
                invalidateAndNotifyUser(user.getId());
            }
        } catch (Exception e) {
            log.error("Caught", e);
        }
        return true;
    }

    private boolean checkIsUserOffline(CachedUser user) {
        SlackResponse response = RestHelper.builder()
                                           .withBaseUrl(configProperties.getSlackUri() + "/api/users.getPresence")
                                           .withBearer(user.getSlackAccessToken())
                                           .getBody(restTemplate, SlackResponse.class);

        if (response.getError() != null && (response.getError().contains("invalid_auth") || response.getError().contains("token_revoked"))) {
            log.trace("Received error response {}", response);
            invalidateAndNotifyUser(user.getId());
            return true;
        }
        boolean isUserActive = "active".equalsIgnoreCase(response.getPresence());
        if (!isUserActive && !user.isCleaned()) {
            log.debug("User {} is away.", user.getId());
            cleanStatus(user);
        }
        return !isUserActive;
    }

    public void invalidateAndNotifyUser(String userId) {
        try {
            log.error("User's Slack token has been invalidated. Cleaning up user {}", userId);
            userCache.invalidate(userId);
            userRepository.deleteById(userId);
            notifyUser("/api/chat.postMessage", new SlackMessage(userId, SPOTIFY_INVALIDATED_MESSAGE), userId);
        } catch (Exception e) {
            log.error("Failed to clean up user properly", e);
        }
    }

    private boolean statusHasBeenManuallyChanged(CachedUser user) {
        return tryCheck(() -> checkStatusHasBeenChanged(user));
    }

    private boolean checkStatusHasBeenChanged(CachedUser user) {
        SlackStatusPayload response = RestHelper.builder()
                                                .withBaseUrl(configProperties.getSlackUri() + "/api/users.profile.get")
                                                .withBearer(user.getSlackAccessToken())
                                                .getBody(restTemplate, SlackStatusPayload.class);

        // Slack escapes reserved characters, see here https://api.slack.com/reference/surfaces/formatting#escaping
        String sanitizedStatus = response.getProfile().getStatusText()
                                         .replace("&amp;", "&")
                                         .replace("&lt;", "<")
                                         .replace("&gt;", ">");
        boolean statusHasBeenManuallyChanged = isNotBlank(sanitizedStatus) &&
            (!sanitizedStatus.equalsIgnoreCase(user.getSlackStatus()) || user.isManualStatus());
        user.setCurrentEmoji(response.getProfile().getStatusEmoji());
        if (statusHasBeenManuallyChanged) {
            log.debug("Status for user {} has been manually changed. Skipping the update.", user.getId());
            user.setManualStatus(true);
        } else {
            user.setManualStatus(false);
        }
        user.setSlackStatus(sanitizedStatus);
        return statusHasBeenManuallyChanged;
    }

    public String notifyUser(String endpoint, Object body, String userId) {
        log.trace("Notifying user at endpoint {} with body {}", endpoint, body);
        //noinspection deprecation: Slack issues warning on missing charset
        return RestHelper.builder()
                         .withBaseUrl(configProperties.getSlackUri() + endpoint)
                         .withBearer(getCachedUser(userId).getSlackBotToken())
                         .withContentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                         .withBody(body)
                         .postAndGetBody(restTemplate, String.class);
    }

    public String pause(String userId) {
        return Optional.ofNullable(userCache.getIfPresent(userId))
                       .map(cachedUser -> {
                           cachedUser.setDisabled(true);
                           cleanStatus(cachedUser);
                           persistState(userId, true);
                           return "Status updates have been paused";
                       })
                       .orElse(MISSING_USER_ERROR);
    }

    public String resume(String userId) {
        return Optional.ofNullable(userCache.getIfPresent(userId))
                       .map(cachedUser -> {
                           persistState(userId, false);
                           cachedUser.setDisabled(false);
                           return "Status updates have been resumed";
                       })
                       .orElse(MISSING_USER_ERROR);
    }

    private void persistState(String userId, boolean isDisabled) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setDisabled(isDisabled);
            userRepository.save(user);
        });
    }

    public String purge(String userId) {
        return Optional.ofNullable(userCache.getIfPresent(userId))
                       .map(cachedUser -> {
                           cleanStatus(cachedUser);
                           userRepository.findById(userId).ifPresent(userRepository::delete);
                           userCache.invalidate(userId);
                           return "User data has been purged. ";
                       })
                       .orElse(MISSING_USER_ERROR);
    }

    private CachedUser getCachedUser(String userId) {
        return Optional.ofNullable(userCache.getIfPresent(userId))
                       .orElseThrow(() -> new UserNotFoundException(MISSING_USER_ERROR));
    }

    public boolean isUserLive(CachedUser cachedUser) {
        if (cachedUser.isDisabled()) {
            log.trace("Skipping the polling for {} since user account is disabled", cachedUser.getId());
            return false;
        }
        if (isInOfflineHours(cachedUser)) {
            log.trace("Skipping the polling for {} outside working hours", cachedUser.getId());
            return false;
        }
        if (isUserOffline(cachedUser)) {
            log.trace("Skipping the polling for {} since user is offline", cachedUser.getId());
            return false;
        }
        if (statusHasBeenManuallyChanged(cachedUser)) {
            log.trace("Skipping the polling for {} since status has been manually updated", cachedUser.getId());
            return false;
        }
        return true;
    }

    private boolean isInOfflineHours(CachedUser user) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int currentTime = now.getHour() * 100 + now.getMinute();
        Integer offlineStart = user.getSyncEndHour();
        Integer offlineEnd = user.getSyncStartHour();
        if (offlineStart == null || offlineEnd == null) {
            offlineStart = configProperties.getSyncEndHr() * 100;
            offlineEnd = configProperties.getSyncStartHr() * 100;
        }

        return offlineEnd > offlineStart && currentTime >= offlineStart && currentTime <= offlineEnd
            || offlineEnd < offlineStart && (currentTime >= offlineStart || currentTime <= offlineEnd);
    }

    @PreDestroy
    public void onDestroy() {
        userCache.asMap().values().forEach(cachedUser -> {
            try {
                log.debug("Cleaning status of user {} before shutdown", cachedUser.getId());
                if (!statusHasBeenManuallyChanged(cachedUser)) {
                    updateStatus(cachedUser, new SlackStatusPayload());
                }
            } catch (Exception e) {
                log.debug("Failed to clean status of user {}", cachedUser.getId());
            }
        });
    }

    private boolean tryCheck(BooleanSupplier supplier) {
        try {
            return supplier.getAsBoolean();
        } catch (Exception e) {
            log.error("Caught", e);
        }
        return false;
    }
}
