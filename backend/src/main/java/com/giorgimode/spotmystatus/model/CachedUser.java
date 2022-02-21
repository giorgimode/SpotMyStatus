package com.giorgimode.spotmystatus.model;


import static com.giorgimode.spotmystatus.helpers.SpotUtil.requireNonBlank;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import com.google.common.base.MoreObjects;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CachedUser implements Serializable {

    private String id;
    private Integer timezoneOffsetSeconds;
    private String slackStatus;
    private boolean manualStatus;
    private String slackAccessToken;
    private String slackBotToken;
    private String spotifyAccessToken;
    private String spotifyRefreshToken;
    private boolean disabled;
    private boolean cleaned = true;
    private LocalDateTime updatedAt;
    private List<String> emojis;
    private String currentEmoji;
    private List<SpotifyItem> spotifyItems;
    private List<String> spotifyDeviceIds;
    private Integer syncStartHour;
    private Integer syncEndHour;

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
            .add("slackAccessToken", slackAccessToken)
            .add("slackBotToken", slackBotToken)
            .add("emojis", emojis)
            .add("spotifyItems", spotifyItems)
            .add("spotifyDeviceIds", spotifyDeviceIds)
            .add("syncStartHour", syncStartHour)
            .add("syncEndHour", syncEndHour)
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
        private String slackBotToken;
        private String spotifyAccessToken;
        private String spotifyRefreshToken;
        private boolean disabled;
        private String emojis;
        private String spotifyItems;
        private String spotifyDeviceIds;
        private Integer syncStartHour;
        private Integer syncEndHour;

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

        public CachedUserBuilder slackBotToken(String slackBotToken) {
            this.slackBotToken = slackBotToken;
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

        public CachedUserBuilder disabled(Boolean disabled) {
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

        public CachedUserBuilder spotifyDeviceIds(String spotifyDeviceIds) {
            this.spotifyDeviceIds = spotifyDeviceIds;
            return this;
        }

        public CachedUserBuilder syncStartHour(Integer syncStartHour) {
            this.syncStartHour = syncStartHour;
            return this;
        }

        public CachedUserBuilder syncEndHour(Integer syncEndHour) {
            this.syncEndHour = syncEndHour;
            return this;
        }

        public CachedUser build() {
            CachedUser cachedUser = new CachedUser();
            cachedUser.setId(requireNonBlank(id));
            cachedUser.setTimezoneOffsetSeconds(requireNonNull(timezoneOffsetSeconds));
            cachedUser.setSlackAccessToken(requireNonBlank(slackAccessToken));
            cachedUser.setSlackBotToken(requireNonBlank(slackBotToken));
            cachedUser.setSpotifyRefreshToken(requireNonBlank(spotifyRefreshToken));
            cachedUser.setSpotifyAccessToken(requireNonBlank(spotifyAccessToken));
            cachedUser.setEmojis(split(emojis));
            cachedUser.setSpotifyItems(splitItems(spotifyItems));
            cachedUser.setSpotifyDeviceIds(split(spotifyDeviceIds));
            cachedUser.setDisabled(disabled);
            cachedUser.setSyncStartHour(syncStartHour);
            cachedUser.setSyncEndHour(syncEndHour);
            return cachedUser;
        }

        private List<String> split(String items) {
            return Optional.ofNullable(items)
                           .filter(not(String::isBlank))
                           .map(field -> field.split(","))
                           .map(Arrays::asList)
                           .orElse(List.of());
        }

        private List<SpotifyItem> splitItems(String list) {
            return Optional.ofNullable(list)
                           .filter(not(String::isBlank))
                           .map(field -> field.split(","))
                           .stream()
                           .flatMap(Arrays::stream)
                           .map(SpotifyItem::from)
                           .collect(Collectors.toList());
        }
    }
}
