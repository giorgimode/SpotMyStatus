package com.giorgimode.SpotMyStatus.spotify;

import static com.giorgimode.SpotMyStatus.spotify.SpotifyScopes.USER_CURRENTLY_PLAYING;
import static com.giorgimode.SpotMyStatus.spotify.SpotifyScopes.USER_TOP_READ;
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
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SpotifyAgent {

    @Autowired
    private SpotifyApi spotifyApi;

    @SneakyThrows
    public void updateAuthToken(String code) {
        AuthorizationCodeRequest authorCodeRequest = spotifyApi.authorizationCode(code).build();
        AuthorizationCodeCredentials codeCredentials = authorCodeRequest.execute();
        spotifyApi.setAccessToken(codeCredentials.getAccessToken());
        spotifyApi.setRefreshToken(codeCredentials.getRefreshToken());
    }

    public URI requestAuthorization() {
        AuthorizationCodeUriRequest authCodeUriRequest = spotifyApi.authorizationCodeUri()
                                                                   .scope(USER_CURRENTLY_PLAYING.scope() + " " + USER_TOP_READ.scope())
                                                                   .build();
        return authCodeUriRequest.execute();
    }


    @SneakyThrows
    public String getCurrentTrack() {
        GetUsersCurrentlyPlayingTrackRequest playingTrackRequest = spotifyApi.getUsersCurrentlyPlayingTrack().build();
        CurrentlyPlaying currentlyPlaying = playingTrackRequest.execute();
        System.out.println("*********Track is currently playing: " + currentlyPlaying.getIs_playing());
        Track track = (Track) currentlyPlaying.getItem();
        String artists = Arrays.stream(track.getArtists()).map(ArtistSimplified::getName).collect(Collectors.joining(", "));
        String currentTrackAndArtist = artists + " - " + track.getName();
        System.out.println("*********Track: " + currentTrackAndArtist);
        return currentTrackAndArtist;
    }
}
