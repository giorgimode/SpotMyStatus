package com.giorgimode.spotmystatus.persistence;


import com.google.common.base.MoreObjects;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "users")
public class User implements Serializable {

    private static final long serialVersionUID = -2343243243242432341L;

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "slack_access_token", unique = true, nullable = false)
    private String slackAccessToken;

    @Column(name = "slack_bot_token", nullable = false)
    private String slackBotToken;

    @Column(name = "spotify_refresh_token", unique = true)
    private String spotifyRefreshToken;

    @Column(name = "state")
    private UUID state;

    @Column(name = "emojis")
    private String emojis;

    @Column(name = "spotify_items")
    private String spotifyItems;

    @Column(name = "spotify_devices")
    private String spotifyDevices;

    @Column(name = "disabled", nullable = false)
    private boolean disabled = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Override
    public String toString() {
        return MoreObjects
            .toStringHelper(this)
            .add("userId", id)
            .add("slackAccessToken", slackAccessToken)
            .add("slackBotToken", slackBotToken)
            .add("spotifyRefreshToken", spotifyRefreshToken)
            .add("state", state)
            .add("disabled", disabled)
            .add("createdAt", createdAt)
            .toString();
    }
}
