package com.giorgimode.SpotMyStatus.model;


import static com.giorgimode.SpotMyStatus.util.SpotUtil.requireNonBlank;
import static java.util.Objects.requireNonNull;
import com.google.common.base.MoreObjects;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.Data;

@Data
public class CachedUser implements Serializable {

    private String id;
    private Integer timezoneOffsetSeconds;
    private String slackStatus;
    private boolean manualStatus;
    private String slackAccessToken;
    private String spotifyAccessToken;
    private String spotifyRefreshToken;
    private boolean disabled;
    private boolean cleaned = true;
    private LocalDateTime updatedAt;
    private List<String> emojis;
    private List<String> spotifyItems;

    @Override
    public String toString() {
        return MoreObjects
            .toStringHelper(this)
            .add("userId", id)
            .add("timezoneOffsetSeconds", timezoneOffsetSeconds)
            .add("slackStatus", slackStatus)
            .add("manualStatus", manualStatus)
            .add("spotifyAccessToken", spotifyAccessToken)
            .add("spotifyRefreshToken", spotifyRefreshToken)
            .add("emojis", emojis)
            .add("spotifyItems", spotifyItems)
            .add("disabled", disabled)
            .add("cleaned", cleaned)
            .add("updatedAt", updatedAt)
            .toString();
    }

    public static CachedUserBuilder builder() {
        return new CachedUserBuilder();
    }

    public static final class CachedUserBuilder {

        private String id;
        private Integer timezoneOffsetSeconds;
        private String slackAccessToken;
        private String spotifyAccessToken;
        private String spotifyRefreshToken;
        private boolean disabled;
        private String emojis;
        private String spotifyItems;

        private CachedUserBuilder() {
        }

        public CachedUserBuilder id(String id) {
            this.id = id;
            return this;
        }

        public CachedUserBuilder timezoneOffsetSeconds(Integer timezoneOffsetSeconds) {
            this.timezoneOffsetSeconds = timezoneOffsetSeconds;
            return this;
        }

        public CachedUserBuilder slackAccessToken(String slackAccessToken) {
            this.slackAccessToken = slackAccessToken;
            return this;
        }

        public CachedUserBuilder spotifyRefreshToken(String spotifyRefreshToken) {
            this.spotifyRefreshToken = spotifyRefreshToken;
            return this;
        }

        public CachedUserBuilder spotifyAccessToken(String spotifyAccessToken) {
            this.spotifyAccessToken = spotifyAccessToken;
            return this;
        }

        public CachedUserBuilder disabled(boolean disabled) {
            this.disabled = disabled;
            return this;
        }

        public CachedUserBuilder emojis(String emojis) {
            this.emojis = emojis;
            return this;
        }

        public CachedUserBuilder spotifyItems(String spotifyItems) {
            this.spotifyItems = spotifyItems;
            return this;
        }

        public CachedUser build() {
            CachedUser cachedUser = new CachedUser();
            cachedUser.setId(requireNonBlank(id));
            cachedUser.setTimezoneOffsetSeconds(requireNonNull(timezoneOffsetSeconds));
            cachedUser.setSlackAccessToken(requireNonBlank(slackAccessToken));
            cachedUser.setSpotifyRefreshToken(requireNonBlank(spotifyRefreshToken));
            cachedUser.setSpotifyAccessToken(requireNonBlank(spotifyAccessToken));
            cachedUser.setEmojis(split(emojis));
            cachedUser.setSpotifyItems(split(spotifyItems));
            cachedUser.setDisabled(disabled);
            return cachedUser;
        }

        private List<String> split(String emojis) {
            return Optional.ofNullable(emojis)
                           .map(field -> field.split(","))
                           .map(Arrays::asList)
                           .orElse(List.of());
        }
    }
}
