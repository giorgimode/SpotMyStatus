package com.giorgimode.spotmystatus.spotify;

import static com.giorgimode.spotmystatus.helpers.SpotConstants.SPOTIFY_REDIRECT_PATH;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.SPOTIFY_SCOPE_USER_PLAYBACK;
import static com.giorgimode.spotmystatus.helpers.SpotUtil.baseUri;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import com.giorgimode.spotmystatus.helpers.OauthProperties;
import com.giorgimode.spotmystatus.helpers.PropertyVault;
import com.giorgimode.spotmystatus.helpers.RestHelper;
import com.giorgimode.spotmystatus.helpers.SpotMyStatusProperties;
import com.giorgimode.spotmystatus.model.SpotifyTokenResponse;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Component
@Slf4j
public class SpotifyAuthClient {

    private final PropertyVault propertyVault;
    private final RestTemplate restTemplate;
    private final SpotMyStatusProperties configProperties;

    public SpotifyAuthClient(PropertyVault propertyVault,
        RestTemplate restTemplate,
        SpotMyStatusProperties configProperties) {

        this.propertyVault = propertyVault;
        this.restTemplate = restTemplate;
        this.configProperties = configProperties;
    }

    public SpotifyTokenResponse getSpotifyTokens(String code) {
        OauthProperties authProps = propertyVault.getSpotify();
        MultiValueMap<String, String> authMap = createAuthenticationProperties(code);
        ResponseEntity<SpotifyTokenResponse> tokenResponse;
        try {
            tokenResponse = RestHelper.builder()
                                      .withBaseUrl(configProperties.getSpotifyAccountUri() + "/api/token")
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
        return tokenResponse.getBody();
    }

    private MultiValueMap<String, String> createAuthenticationProperties(String code) {
        MultiValueMap<String, String> authMap = new LinkedMultiValueMap<>();
        authMap.add("grant_type", "authorization_code");
        authMap.add("code", code);
        authMap.add("redirect_uri", getRedirectUri());
        return authMap;
    }

    public String requestAuthorization(UUID state) {
        return RestHelper.builder()
                         .withBaseUrl(configProperties.getSpotifyAccountUri() + "/authorize")
                         .withQueryParam("client_id", propertyVault.getSpotify().getClientId())
                         .withQueryParam("response_type", "code")
                         .withQueryParam("redirect_uri", getRedirectUri())
                         .withQueryParam("scope", SPOTIFY_SCOPE_USER_PLAYBACK)
                         .withQueryParam("state", state)
                         .createUri();
    }

    private String getRedirectUri() {
        return baseUri(configProperties.getRedirectUriScheme()) + "/api" + SPOTIFY_REDIRECT_PATH;
    }

    public SpotifyTokenResponse getNewAccessToken(String refreshToken) {
        log.info("Retrieving new access token");
        MultiValueMap<String, String> authMap = new LinkedMultiValueMap<>();
        authMap.add("grant_type", "refresh_token");
        authMap.add("refresh_token", refreshToken);
        return RestHelper.builder()
                         .withBaseUrl(configProperties.getSpotifyAccountUri() + "/api/token")
                         .withBasicAuth(propertyVault.getSpotify().getClientId(), propertyVault.getSpotify().getClientSecret())
                         .withContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                         .withBody(authMap)
                         .postAndGetBody(restTemplate, SpotifyTokenResponse.class);
    }
}
