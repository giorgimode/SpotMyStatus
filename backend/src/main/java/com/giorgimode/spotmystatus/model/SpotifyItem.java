package com.giorgimode.spotmystatus.model;

import static org.apache.commons.lang3.StringUtils.isBlank;
import com.giorgimode.spotmystatus.exceptions.InvalidConfigurationException;

public enum SpotifyItem {
    EPISODE,
    TRACK;

    public String title() {
        return name().toLowerCase();
    }

    public static SpotifyItem from(String item) {
        if (isBlank(item)) {
            return null;
        }
        for (SpotifyItem value : values()) {
            if (value.name().equalsIgnoreCase(item)) {
                return value;
            }
        }
        throw new InvalidConfigurationException("Invalid spotify item value " + item);
    }
}
