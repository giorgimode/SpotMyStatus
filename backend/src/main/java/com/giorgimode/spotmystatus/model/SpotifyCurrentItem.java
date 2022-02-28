package com.giorgimode.spotmystatus.model;

import static com.giorgimode.spotmystatus.helpers.SpotUtil.safeGet;
import static com.giorgimode.spotmystatus.model.SpotifyItem.EPISODE;
import static com.giorgimode.spotmystatus.model.SpotifyItem.TRACK;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Data
@Slf4j
public class SpotifyCurrentItem {

    @JsonProperty(value = "is_playing", required = true)
    private Boolean isPlaying;

    @JsonProperty(value = "currently_playing_type", required = true)
    private String type;

    @JsonProperty(value = "progress_ms", required = true)
    private Long progressMs = 0L;

    private Long durationMs;

    private String title;

    private List<String> artists;

    private SpotifyDevice device;

    private String trackUrl;

    private String imageUrl;

    @JsonProperty("item")
    private void unpackNestedItem(Map<String, Object> item) {
        if (item == null) {
            log.debug("Spotify item is null");
            return;
        }
        this.durationMs = ((Number) item.get("duration_ms")).longValue();
        this.title = (String) item.get("name");

        setTrackUrl(item);
        setArtistsAndImageUrl(item);
    }

    private void setTrackUrl(Map<String, Object> item) {
        Map<String, Object> externalUrl = safeGet(item, "external_urls");
        this.trackUrl = safeGet(externalUrl, "spotify");
    }

    private void setArtistsAndImageUrl(Map<String, Object> item) {
        String receivedType = (String) item.get("type");
        if (TRACK.title().equalsIgnoreCase(receivedType)) {
            setTrackArtists(item);
            setTrackImageUrl(item);
        } else if (EPISODE.title().equalsIgnoreCase(receivedType)) {
            setEpisodeArtists(item);
            setEpisodeImageUrl(item);
        } else {
            log.warn("Cannot parse unknown item type {}", receivedType);
        }
    }

    private void setEpisodeImageUrl(Map<String, Object> item) {
        List<Map<String, String>> images = safeGet(item, "images");
        setImageUrl(images);
    }

    private void setTrackImageUrl(Map<String, Object> item) {
        Map<String, Object> album = safeGet(item, "album");
        List<Map<String, String>> images = safeGet(album, "images");
        setImageUrl(images);
    }

    private void setImageUrl(List<Map<String, String>> images) {
        this.imageUrl = Optional.ofNullable(images)
                                .map(imageList -> imageList.get(0))
                                .map(imageMap -> imageMap.get("url"))
                                .orElse(null);
    }

    private void setEpisodeArtists(Map<String, Object> item) {
        Map<String, Object> show = safeGet(item, "show", Map.of());
        this.artists = List.of((String) show.get("name"));
    }

    @SuppressWarnings("unchecked")
    private void setTrackArtists(Map<String, Object> item) {
        List<Map<String, Object>> trackArtists = (List<Map<String, Object>>) item.get("artists");
        this.artists = trackArtists.stream()
                                   .map(artistProperties -> (String) artistProperties.get("name"))
                                   .collect(Collectors.toList());
    }

    public String generateFullTitle(int maxAllowedLength) {
        String newStatus = EPISODE.title().equals(getType()) ? "PODCAST: " : "";
        newStatus += String.join(", ", getArtists()) + " - " + getTitle();
        if (newStatus.length() > maxAllowedLength) {
            String firstArtistOnly = getArtists().get(0);
            newStatus = StringUtils.abbreviate(firstArtistOnly + " - " + getTitle(), 100);
        }
        return newStatus;
    }
}
