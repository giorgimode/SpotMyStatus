create table if not exists users
(
    id varchar(255) primary key,
    team_id varchar(255) not null,
    slack_access_token text not null,
    slack_bot_token text not null,
    spotify_refresh_token text,
    tz_offset_sec integer not null,
    state uuid,
    disabled boolean,
    created_at timestamp,
    emojis text,
    spotify_items text,
    sync_from smallint,
    sync_to smallint,
    spotify_devices text
);