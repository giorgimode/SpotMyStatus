package com.giorgimode.spotmystatus.spotify;

import com.giorgimode.spotmystatus.model.CachedUser;
import com.giorgimode.spotmystatus.model.SpotifyCurrentItem;
import com.giorgimode.spotmystatus.model.SpotifyDevice;
import com.giorgimode.spotmystatus.model.SpotifyDevices;
import com.giorgimode.spotmystatus.model.SpotifyTokenResponse;
import com.giorgimode.spotmystatus.persistence.User;
import com.giorgimode.spotmystatus.persistence.UserRepository;
import com.giorgimode.spotmystatus.service.UserInteractionService;
import com.giorgimode.spotmystatus.util.RestHelper;
import com.giorgimode.spotmystatus.util.SpotUtil;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class SpotifyClient {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${spotify_api_uri}")
    private String spotifyApiUri;

    @Autowired
    private LoadingCache<String, CachedUser> userCache;

    @Autowired
    private SpotifyAuthClient spotifyAuthClient;

    @Autowired
    private UserInteractionService cleanupService;

    public String requestAuthorization(UUID state) {
        return spotifyAuthClient.requestAuthorization(state);
    }

    public void updateAuthToken(String code, UUID state) {
        SpotifyTokenResponse spotifyTokens = spotifyAuthClient.getSpotifyTokens(code);
        log.info("Retrieved spotify access token expiring in {} seconds", spotifyTokens.getExpiresIn());
        User user = userRepository.findByState(state);
        user.setSpotifyRefreshToken(spotifyTokens.getRefreshToken());
        userRepository.save(user);
        CachedUser oldCachedUser = userCache.getIfPresent(user.getId());
        CachedUser newCachedUser = SpotUtil.toCachedUser(user, spotifyTokens.getAccessToken());
        if (oldCachedUser != null) {
            newCachedUser.setSlackStatus(oldCachedUser.getSlackStatus());
        }
        userCache.put(user.getId(), newCachedUser);
    }

    public Optional<SpotifyCurrentItem> getCurrentTrack(CachedUser user) {
        try {
            return tryGetSpotifyCurrentTrack(user);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                return refreshSpotifyAccessToken(user);
            } else if (ex.getStatusCode() == HttpStatus.BAD_REQUEST && ex.getResponseBodyAsString().contains("invalid_grant")) {
                log.error("User's Spotify token has been invalidated. Cleaning up user {}", user.getId());
                cleanupService.invalidateAndNotifyUser(user.getId());
            } else {
                log.error("Failed to retrieve current track for user {}", user.getId(), ex);
            }
        } catch (Exception e) {
            log.error("Failed to retrieve current track for user {}", user.getId(), e);
        }
        return Optional.empty();
    }

    private Optional<SpotifyCurrentItem> refreshSpotifyAccessToken(CachedUser user) {
        try {
            SpotifyTokenResponse spotifyTokens = spotifyAuthClient.getNewAccessToken(user.getSpotifyRefreshToken());
            log.info("Retrieved spotify access token expiring in {} seconds", spotifyTokens.getExpiresIn());
            user.setSpotifyAccessToken(spotifyTokens.getAccessToken());
            return tryGetSpotifyCurrentTrack(user);
        } catch (Exception e) {
            log.error("Failed to retrieve current track", e);
            return Optional.empty();
        }
    }

    private Optional<SpotifyCurrentItem> tryGetSpotifyCurrentTrack(CachedUser user) {
        SpotifyCurrentItem currentItem = RestHelper.builder()
                                                   .withBaseUrl(spotifyApiUri + "/v1/me/player")
                                                   .withBearer(user.getSpotifyAccessToken())
                                                   .withQueryParam("additional_types", "track,episode")
                                                   .getBody(restTemplate, SpotifyCurrentItem.class);
        if (currentItem == null || isPrivateSession(user.getId(), currentItem) || currentItem.getTitle() == null
            || currentItem.getIsPlaying() == null) {
            return Optional.empty();
        }
        return Optional.of(currentItem);
    }

    private boolean isPrivateSession(String userId, SpotifyCurrentItem spotifyCurrentItem) {
        boolean isPrivateSession = spotifyCurrentItem.getDevice().isPrivateSession();
        if (isPrivateSession) {
            log.debug("Skipping syncing, since user {} is in private Spotify session", userId);
        }
        return isPrivateSession;
    }

    public List<SpotifyDevice> getSpotifyDevices(CachedUser user) {
        try {
            SpotifyDevices spotifyDevices = RestHelper.builder()
                                                      .withBaseUrl(spotifyApiUri + "/v1/me/player/devices")
                                                      .withBearer(user.getSpotifyAccessToken())
                                                      .getBody(restTemplate, SpotifyDevices.class);
            if (spotifyDevices == null || spotifyDevices.getDevices() == null) {
                return List.of();
            }
            return spotifyDevices.getDevices();
        } catch (Exception e) {
            log.error("Failed to retrieve Spotify devices for user {}", user.getId(), e);
            return List.of();
        }
    }
}