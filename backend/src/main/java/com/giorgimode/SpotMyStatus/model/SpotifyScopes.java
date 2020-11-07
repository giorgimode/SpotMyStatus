package com.giorgimode.SpotMyStatus.model;

public enum SpotifyScopes {
    USER_CURRENTLY_PLAYING("user-read-currently-playing"),
    USER_TOP_READ("user-top-read");

    private final String scope;

    SpotifyScopes(String scope) {
        this.scope = scope;
    }

    public String scope() {
        return scope;
    }
}
