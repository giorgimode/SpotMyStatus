package com.giorgimode.spotmystatus.spotify;

import static com.giorgimode.spotmystatus.common.SpotConstants.SPOTIFY_REDIRECT_PATH;
import static com.giorgimode.spotmystatus.model.SpotifyScopes.USER_CURRENTLY_PLAYING;
import static com.giorgimode.spotmystatus.util.SpotUtil.baseUri;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import com.giorgimode.spotmystatus.common.OauthProperties;
import com.giorgimode.spotmystatus.common.PropertyVault;
import com.giorgimode.spotmystatus.model.SpotifyTokenResponse;
import com.giorgimode.spotmystatus.util.RestHelper;
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

@Component
@Slf4j
public class SpotifyAuthClient {

    @Autowired
    private PropertyVault propertyVault;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${spotify_account_uri}")
    private String spotifyAccountUri;


    @Value("${redirect_uri_scheme}")
    private String uriScheme;

    public SpotifyTokenResponse getSpotifyTokens(String code) {
        OauthProperties authProps = propertyVault.getSpotify();
        MultiValueMap<String, String> authMap = createAuthenticationProperties(code);
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
        return tokenResponse.getBody();
    }

    private MultiValueMap<String, String> createAuthenticationProperties(String code) {
        MultiValueMap<String, String> authMap = new LinkedMultiValueMap<>();
        authMap.add("grant_type", "authorization_code");
        authMap.add("code", code);
        authMap.add("redirect_uri", baseUri(uriScheme) + SPOTIFY_REDIRECT_PATH);
        return authMap;
    }

    public String requestAuthorization(UUID state) {
        return RestHelper.builder()
                         .withBaseUrl(spotifyAccountUri + "/authorize")
                         .withQueryParam("client_id", propertyVault.getSpotify().getClientId())
                         .withQueryParam("response_type", "code")
                         .withQueryParam("redirect_uri", baseUri(uriScheme) + SPOTIFY_REDIRECT_PATH)
                         .withQueryParam("scope", USER_CURRENTLY_PLAYING.scope())
                         .withQueryParam("state", state)
                         .createUri();
    }

    public SpotifyTokenResponse getNewAccessToken(String refreshToken) {
        log.info("Retrieving new access token");
        MultiValueMap<String, String> authMap = new LinkedMultiValueMap<>();
        authMap.add("grant_type", "refresh_token");
        authMap.add("refresh_token", refreshToken);
        return RestHelper.builder()
                         .withBaseUrl(spotifyAccountUri + "/api/token")
                         .withBasicAuth(propertyVault.getSpotify().getClientId(), propertyVault.getSpotify().getClientSecret())
                         .withContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                         .withBody(authMap)
                         .postAndGetBody(restTemplate, SpotifyTokenResponse.class);
    }
}