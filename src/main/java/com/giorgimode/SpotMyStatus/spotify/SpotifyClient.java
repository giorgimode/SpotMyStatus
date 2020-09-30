package com.giorgimode.SpotMyStatus.spotify;

import static com.giorgimode.SpotMyStatus.common.SpotConstants.SPOTIFY_REDIRECT_PATH;
import static com.giorgimode.SpotMyStatus.spotify.SpotifyScopes.USER_CURRENTLY_PLAYING;
import static com.giorgimode.SpotMyStatus.spotify.SpotifyScopes.USER_TOP_READ;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import com.giorgimode.SpotMyStatus.common.OauthProperties;
import com.giorgimode.SpotMyStatus.common.PropertyVault;
import com.giorgimode.SpotMyStatus.model.CachedUser;
import com.giorgimode.SpotMyStatus.model.SpotifyCurrentTrackResponse;
import com.giorgimode.SpotMyStatus.model.SpotifyTokenResponse;
import com.giorgimode.SpotMyStatus.persistence.User;
import com.giorgimode.SpotMyStatus.persistence.UserRepository;
import com.giorgimode.SpotMyStatus.util.RestHelper;
import com.giorgimode.SpotMyStatus.util.SpotUtil;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
@Slf4j
public class SpotifyClient {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PropertyVault propertyVault;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${spotify_account_uri}")
    private String spotifyAccountUri;

    @Value("${spotify_api_uri}")
    private String spotifyApiUri;

    @Autowired
    private LoadingCache<String, CachedUser> userCache;

    public String requestAuthorization(UUID state) {
        return RestHelper.builder()
                         .withBaseUrl(spotifyAccountUri + "/authorize")
                         .withQueryParam("client_id", propertyVault.getSpotify().getClientId())
                         .withQueryParam("response_type", "code")
                         .withQueryParam("redirect_uri", baseUri() + SPOTIFY_REDIRECT_PATH)
                         .withQueryParam("scope", USER_CURRENTLY_PLAYING.scope() + " " + USER_TOP_READ.scope())
                         .withQueryParam("state", state)
                         .createUri();
    }

    public void updateAuthToken(String code, UUID state) {
        OauthProperties authProps = propertyVault.getSpotify();
        MultiValueMap<String, String> authMap = createAuthenticationProperties(code, authProps);
        ResponseEntity<SpotifyTokenResponse> tokenResponse;
        try {
            tokenResponse = RestHelper.builder()
                                      .withBaseUrl(spotifyAccountUri + "/api/token")
                                      .withBasicAuth(authProps.getClientId(), authProps.getClientSecret())
                                      .withContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                                      .withBody(authMap)
                                      .post(restTemplate, SpotifyTokenResponse.class);
        } catch (Exception e) {
            log.error("Failed to authorize user with code {}", code);
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        if (tokenResponse.getBody() == null) {
            log.error("Failed to authorize user with code {}", code);
            throw new ResponseStatusException(UNAUTHORIZED);
        }
        SpotifyTokenResponse spotifyTokens = tokenResponse.getBody();
        User user = userRepository.findByState(state);
        user.setSpotifyRefreshToken(spotifyTokens.getRefreshToken());
        user.setSpotifyAccessToken(spotifyTokens.getAccessToken());
        userRepository.save(user);
        userCache.get(user.getId(), key -> SpotUtil.toCachedUser(user));
    }

    private MultiValueMap<String, String> createAuthenticationProperties(String code, OauthProperties authProps) {
        MultiValueMap<String, String> authMap = new LinkedMultiValueMap<>();
        authMap.add("grant_type", "authorization_code");
        authMap.add("code", code);
        authMap.add("redirect_uri", baseUri() + SPOTIFY_REDIRECT_PATH);
        return authMap;
    }

    private static String baseUri() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    public Optional<SpotifyCurrentTrackResponse> getCurrentTrack(String accessToken) {
        try {
            SpotifyCurrentTrackResponse currentTrackResponse = RestHelper.builder()
                                                                         .withBaseUrl(spotifyApiUri + "/v1/me/player/currently-playing")
                                                                         .withBearer(accessToken)
                                                                         .getBody(restTemplate, SpotifyCurrentTrackResponse.class);
            if (currentTrackResponse.getSongTitle() == null || currentTrackResponse.getIsPlaying() == null) {
                return Optional.empty();
            }
            return Optional.of(currentTrackResponse);
        } catch (Exception e) {
            log.error("Failed to retrieve current track", e);
            return Optional.empty();
        }
    }
}
