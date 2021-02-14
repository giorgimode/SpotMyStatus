# SpotMyStatus
Slack App updating user's status with currently playing song from Spotify

### [App Home page](https://spotmystatus.giomo.de)

[![here](/frontend/img/spotify-slack.png?raw=true)](https://spotmystatus.giomo.de)

## Bugs, Feature Requests
Feel free to create a support ticket [via home page](https://spotmystatus.giomo.de/support) 
or to add a ticket [directly in the issue tracker](https://giorgimode.myjetbrains.com/youtrack/issues/SMS)


## SpotMyStatus app commands
Following parameters can be passed to `/spotme` command in Slack
* /spotme pause
* /spotme play
* /spotme purge
* /spotme help

## SpotMyStatus customization
Customize your experience by running `/spotme` command or by accessing app Home Tab
![SpotMyStatus Home Tab Screenshot](/frontend/img/github_screenshot.png?raw=true)

        
## Features
* User can pause/play status syncing
* User can choose to sync music and/or podcasts (default both)
* User can define emojis to rotate from when app sets a status. Emojis not present in the workspace will not be added 
(default 🎧, 🎵, 🎶)
* User can define Spotify devices to sync from (default all)
* User can purge all their data from SpotMyStatus server

On each status update the status emoji is randomly picked from `:headphones:`, `:musical_note:` or `:notes:`.

On each poll the application does various checks:
* has user paused status updates
* is user offline
* has user manually changed their status(to avoid overwriting it)

If any of the above conditions are true, status is not updated.