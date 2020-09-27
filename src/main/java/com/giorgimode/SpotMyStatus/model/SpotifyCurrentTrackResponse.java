package com.giorgimode.SpotMyStatus.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;

@Data
public class SpotifyCurrentTrackResponse {

    @JsonProperty("is_playing")
    private boolean isPlaying;

    @JsonProperty("currently_playing_type")
    private String currentlyPlayingType;

    @JsonProperty("progress_ms")
    private Long progressMs = 0L;

    private Long durationMs;

    @JsonProperty("name")
    private String songTitle;

    private String artists;

    @SuppressWarnings("unchecked")
    @JsonProperty("item")
    private void unpackNested(Map<String, Object> item) {
        List<Map<String, Object>> artists = (List<Map<String, Object>>) item.get("artists");
        this.durationMs = ((Number) item.get("duration_ms")).longValue();
        this.songTitle = (String) item.get("name");
        this.artists = artists.stream()
                              .map(artistProperties -> (String) artistProperties.get("name"))
                              .collect(Collectors.joining(", "));
    }
}
