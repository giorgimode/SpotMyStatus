package com.giorgimode.SpotMyStatus.model;

import static com.giorgimode.SpotMyStatus.model.SpotifyItem.EPISODE;
import static com.giorgimode.SpotMyStatus.model.SpotifyItem.TRACK;
import static com.giorgimode.SpotMyStatus.util.SpotUtil.safeGet;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class SpotifyCurrentItem {

    @JsonProperty(value = "is_playing", required = true)
    private Boolean isPlaying;

    @JsonProperty(value = "currently_playing_type", required = true)
    private SpotifyItem type;

    @JsonProperty(value = "progress_ms", required = true)
    private Long progressMs = 0L;

    private Long durationMs;

    private String title;

    private List<String> artists;

    @SuppressWarnings("unchecked")
    @JsonProperty("item")
    private void unpackNested(Map<String, Object> item) {
        this.durationMs = ((Number) item.get("duration_ms")).longValue();
        this.title = (String) item.get("name");

        String receivedType = (String) item.get("type");
        if (TRACK.title().equalsIgnoreCase(receivedType)) {
            List<Map<String, Object>> trackArtists = (List<Map<String, Object>>) item.get("artists");
            this.artists = trackArtists.stream()
                                       .map(artistProperties -> (String) artistProperties.get("name"))
                                       .collect(Collectors.toList());
        } else if (EPISODE.title().equalsIgnoreCase(receivedType)) {
            Map<String, Object> show = safeGet(item, "show", Map.of());
            artists = List.of((String) show.get("name"));
        } else {
            log.warn("Cannot parse unknown item type {}", receivedType);
        }

    }
}
