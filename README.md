# SpotMyStatus

Slack App updating user's status with currently playing song from Spotify


## Bugs, Feature Requests
Feel free to add feature requests to the issue tracker
 
[![here](https://upload.wikimedia.org/wikipedia/commons/4/48/Icon_YouTrack.png)](https://giorgimode.myjetbrains.com/youtrack/issues/SMS)



## SpotMyStatus Slack App Parameters
Following parameters can be passed to `/spotmystatus` command in Slack
* `pause`/`stop` to temporarily pause status updates
* `unpause`/`play`/`resume` to resume status updates
* `purge`/`remove` to purge all user data. Fresh signup will be needed to use the app again
        
## Features
After the signup, SpotMyStatus polls users Spotify account(`spotmystatus.polling_rate`) in normal working hours 
(`spotmystatus.passivate_start_hr`, `spotmystatus.passivate_end_hr`). Outside working hours polling rate is 
decreased(`spotmystatus.passive_polling_probability`) if and only if user has been passive for a certain period 
(`spotmystatus.passivate_after_min`). Each poll is limited to a threshold(`spotmystatus.timeout`) to avoid hanging calls. 

On each status update the status emoji is randomly picked from `:headphones:`, `:musical_note:` or `:notes:`.

On each poll the application does various checks:
* has user paused status updates
* is it outside working hours(between 9PM and 6AM in user's timezone) and has user been passive for a certain period
* is user offline
* has user manually changed status(to avoid overwriting it)

If any of the above conditions are true, status is not updated.