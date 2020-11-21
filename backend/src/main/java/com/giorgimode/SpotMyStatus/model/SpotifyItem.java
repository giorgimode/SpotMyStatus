package com.giorgimode.SpotMyStatus.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SpotifyItem {

    @JsonProperty("episode")
    EPISODE,
    @JsonProperty("track")
    TRACK
}
