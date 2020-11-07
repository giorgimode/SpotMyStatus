package com.giorgimode.SpotMyStatus.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;

@Data
public class SpotifyCurrentTrackResponse {

    @JsonProperty(value = "is_playing", required = true)
    private Boolean isPlaying;

    @JsonProperty(value = "currently_playing_type", required = true)
    private String currentlyPlayingType;

    @JsonProperty(value = "progress_ms", required = true)
    private Long progressMs = 0L;

    private Long durationMs;

    private String songTitle;

    private List<String> artists;

    @SuppressWarnings("unchecked")
    @JsonProperty("item")
    private void unpackNested(Map<String, Object> item) {
        List<Map<String, Object>> artists = (List<Map<String, Object>>) item.get("artists");
        this.durationMs = ((Number) item.get("duration_ms")).longValue();
        this.songTitle = (String) item.get("name");
        this.artists = artists.stream()
                              .map(artistProperties -> (String) artistProperties.get("name"))
                              .collect(Collectors.toList());
    }
}
