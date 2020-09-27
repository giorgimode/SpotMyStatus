package com.giorgimode.SpotMyStatus.spotify;

import static com.giorgimode.SpotMyStatus.spotify.SpotifyScopes.USER_CURRENTLY_PLAYING;
import static com.giorgimode.SpotMyStatus.spotify.SpotifyScopes.USER_TOP_READ;
import com.giorgimode.SpotMyStatus.beans.OauthProperties;
import com.giorgimode.SpotMyStatus.beans.PropertyVault;
import com.giorgimode.SpotMyStatus.model.SpotifyCurrentTrackResponse;
import com.giorgimode.SpotMyStatus.model.SpotifyTokenResponse;
import com.giorgimode.SpotMyStatus.persistence.User;
import com.giorgimode.SpotMyStatus.persistence.UserRepository;
import com.giorgimode.SpotMyStatus.util.RestHelper;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class SpotifyAgent {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PropertyVault propertyVault;

    @Autowired
    private RestTemplate restTemplate;

    public String requestAuthorization(UUID state) {
        return RestHelper.builder()
                         .withBaseUrl("https://accounts.spotify.com/authorize")
                         .withQueryParam("client_id", propertyVault.getSpotify().getClientId())
                         .withQueryParam("response_type", "code")
                         .withQueryParam("redirect_uri", propertyVault.getSpotify().getRedirectUri())
                         .withQueryParam("scope", USER_CURRENTLY_PLAYING.scope() + " " + USER_TOP_READ.scope())
                         .withQueryParam("state", state)
                         .createUri();
    }

    public void updateAuthToken(String code, UUID state) {
        OauthProperties spotifyProps = propertyVault.getSpotify();
        MultiValueMap<String, String> spotifyTokenRequest = new LinkedMultiValueMap<>();
        spotifyTokenRequest.add("grant_type", "authorization_code");
        spotifyTokenRequest.add("code", code);
        spotifyTokenRequest.add("redirect_uri", spotifyProps.getRedirectUri().toString());

        ResponseEntity<SpotifyTokenResponse> tokenResponse = RestHelper.builder()
                                                                       .withBaseUrl("https://accounts.spotify.com/api/token")
                                                                       .withBasicAuth(spotifyProps.getClientId(), spotifyProps.getClientSecret())
                                                                       .withContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                                                                       .withBody(spotifyTokenRequest)
                                                                       .post(restTemplate, SpotifyTokenResponse.class);
        //todo check tokenResponse is not null
        SpotifyTokenResponse spotifyTokens = tokenResponse.getBody();
        User user = userRepository.findByState(state);
        user.setSpotifyRefreshToken(spotifyTokens.getRefreshToken());
        user.setSpotifyAccessToken(spotifyTokens.getAccessToken());
        userRepository.save(user);
    }

    public SpotifyCurrentTrackResponse getCurrentTrack(String accessToken) {
        return RestHelper.builder()
                         .withBaseUrl("https://api.spotify.com/v1/me/player/currently-playing")
                         .withBearer(accessToken)
                         .getBody(restTemplate, SpotifyCurrentTrackResponse.class);
    }
}
