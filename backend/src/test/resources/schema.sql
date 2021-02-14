create table if not exists users
(
    id varchar(255) primary key,
    slack_access_token text not null,
    slack_bot_token text not null,
    spotify_refresh_token text,
    state uuid,
    disabled boolean,
    created_at timestamp,
    emojis text,
    spotify_items text,
    spotify_devices text
);