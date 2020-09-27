package com.giorgimode.SpotMyStatus.entity;


import com.google.common.base.MoreObjects;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "users")
public class User implements Serializable {

    private static final long serialVersionUID = -2343243243242432341L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "slack_access_token", unique = true, nullable = false)
    private String slackAccessToken;

    @Column(name = "spotify_refresh_token", unique = true)
    private String spotifyRefreshToken;

    @Column(name = "spotify_access_token", unique = true)
    private String spotifyAccessToken;

    @Column(name = "state", unique = true)
    private UUID state;

    @Column(name = "slack_status")
    private String slackStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Override
    public String toString() {
        return MoreObjects
            .toStringHelper(this)
            .add("userId", id)
            .add("email", email)
            .add("slackAccessToken", slackAccessToken)
            .add("spotifyRefreshToken", spotifyRefreshToken)
            .add("spotifyAccessToken", spotifyAccessToken)
            .add("state", state)
            .add("slackStatus", slackStatus)
            .add("createdAt", createdAt)
            .add("updatedAt", updatedAt)
            .toString();
    }
}
