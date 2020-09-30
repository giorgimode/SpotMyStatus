package com.giorgimode.SpotMyStatus.model;


import com.google.common.base.MoreObjects;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CachedUser implements Serializable {

    //todo builder + validator
    private String id;
    private Integer timezoneOffsetSeconds;
    private String slackStatus;
    private String slackAccessToken;
    private String spotifyAccessToken;
    private boolean disabled = false;
    private boolean cleaned = false;
    private LocalDateTime updatedAt;

    @Override
    public String toString() {
        return MoreObjects
            .toStringHelper(this)
            .add("userId", id)
            .add("timezoneOffsetSeconds", timezoneOffsetSeconds)
            .add("slackStatus", slackStatus)
            .add("disabled", disabled)
            .add("cleaned", cleaned)
            .add("updatedAt", updatedAt)
            .toString();
    }
}
