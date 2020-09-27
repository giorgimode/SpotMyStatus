package com.giorgimode.SpotMyStatus.spotify;

import static com.giorgimode.SpotMyStatus.spotify.SpotifyScopes.USER_CURRENTLY_PLAYING;
import static com.giorgimode.SpotMyStatus.spotify.SpotifyScopes.USER_TOP_READ;
import com.giorgimode.SpotMyStatus.persistence.User;
import com.giorgimode.SpotMyStatus.persistence.UserRepository;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import com.wrapper.spotify.requests.data.player.GetUsersCurrentlyPlayingTrackRequest;
import java.net.URI;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SpotifyAgent {

    @Autowired
    private SpotifyApi spotifyApi;

    @Autowired
    private UserRepository userRepository;

    public URI requestAuthorization(UUID state) {
        AuthorizationCodeUriRequest authCodeUriRequest = spotifyApi.authorizationCodeUri()
                                                                   .scope(USER_CURRENTLY_PLAYING.scope() + " " + USER_TOP_READ.scope())
                                                                   .state(state.toString())
                                                                   .build();
        return authCodeUriRequest.execute();
    }

    @SneakyThrows
    public void updateAuthToken(String code, UUID state) {
        AuthorizationCodeRequest authorCodeRequest = spotifyApi.authorizationCode(code).build();
        AuthorizationCodeCredentials codeCredentials = authorCodeRequest.execute();
        spotifyApi.setAccessToken(codeCredentials.getAccessToken());
        spotifyApi.setRefreshToken(codeCredentials.getRefreshToken());

        User user = userRepository.findByState(state);
        user.setSpotifyRefreshToken(codeCredentials.getRefreshToken());
        user.setSpotifyAccessToken(codeCredentials.getAccessToken());
        userRepository.save(user);
    }

    @SneakyThrows
    public String getCurrentTrack(String accessToken) {
        spotifyApi.setAccessToken(accessToken);
        return getCurrentTrack();
    }

    @SneakyThrows
    public String getCurrentTrack() {
        GetUsersCurrentlyPlayingTrackRequest playingTrackRequest = spotifyApi.getUsersCurrentlyPlayingTrack().build();
        CurrentlyPlaying currentlyPlaying = playingTrackRequest.execute();
        log.info("*********Track is currently playing: {}", currentlyPlaying.getIs_playing());
        Track track = (Track) currentlyPlaying.getItem();
        String artists = Arrays.stream(track.getArtists()).map(ArtistSimplified::getName).collect(Collectors.joining(", "));
        String currentTrackAndArtist = artists + " - " + track.getName();
        log.info("*********Track: {}", currentTrackAndArtist);
        return currentTrackAndArtist;
    }
}
