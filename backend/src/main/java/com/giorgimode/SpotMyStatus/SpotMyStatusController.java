package com.giorgimode.SpotMyStatus;

import static com.giorgimode.SpotMyStatus.common.SpotConstants.SLACK_REDIRECT_PATH;
import static com.giorgimode.SpotMyStatus.common.SpotConstants.SPOTIFY_REDIRECT_PATH;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.ACTION_ID_EMOJI_ADD;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.ACTION_ID_EMOJI_REMOVE;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.BLOCK_ID_EMOJI_INPUT;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.BLOCK_ID_EMOJI_LIST;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.BLOCK_ID_SPOTIFY_ITEMS;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.BLOCK_ID_SYNC_TOGGLE;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.EMOJI_LIST_FORMAT;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.PAYLOAD_TYPE_BLOCK_ACTIONS;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.PAYLOAD_TYPE_SUBMISSION;
import static java.util.function.Predicate.not;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import com.giorgimode.SpotMyStatus.helpers.SlackModalConverter;
import com.giorgimode.SpotMyStatus.model.SlackEvent;
import com.giorgimode.SpotMyStatus.model.modals.Action;
import com.giorgimode.SpotMyStatus.model.modals.Block;
import com.giorgimode.SpotMyStatus.model.modals.SlackModalIn;
import com.giorgimode.SpotMyStatus.model.modals.SlackModalOut;
import com.giorgimode.SpotMyStatus.model.modals.state.StateValue;
import com.giorgimode.SpotMyStatus.slack.SlackInteractionClient;
import com.giorgimode.SpotMyStatus.slack.SlackPollingClient;
import com.giorgimode.SpotMyStatus.spotify.SpotifyClient;
import com.giorgimode.SpotMyStatus.util.RestHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Slf4j
public class SpotMyStatusController {

    private static final List<String> PAUSE_COMMANDS = List.of("pause", "stop");
    private static final List<String> RESUME_COMMANDS = List.of("unpause", "play", "resume");
    private static final List<String> REMOVE_COMMANDS = List.of("remove", "purge");

    @Autowired
    private SpotifyClient spotifyClient;

    @Autowired
    private SlackPollingClient slackPollingClient;

    @Autowired
    private SlackInteractionClient slackInteractionClient;

    @Autowired
    private RestTemplate restTemplate;

    @RequestMapping("/start")
    public void startNewUser(HttpServletResponse httpServletResponse) {
        log.info("Starting authorization for a new user...");
        String authorization = slackPollingClient.requestAuthorization();
        httpServletResponse.setHeader("Location", authorization);
        httpServletResponse.setStatus(302);
    }

    @RequestMapping(SLACK_REDIRECT_PATH)
    public void slackRedirect(@RequestParam(value = "code", required = false) String slackCode,
        @RequestParam(value = "error", required = false) String error,
        HttpServletResponse httpServletResponse) throws IOException {

        if (slackCode == null) {
            log.info("User failed to grant permission on Slack. Received error {}", error);
            httpServletResponse.sendRedirect("/error.html");
            return;
        }

        log.info("User has granted permission on Slack. Received code {}", slackCode);
        UUID state = slackPollingClient.updateAuthToken(slackCode);

        log.info("Slack authorization successful using code {} and state {}. Requesting authorization on spotify", slackCode, state);
        String authorization = spotifyClient.requestAuthorization(state);
        httpServletResponse.setHeader("Location", authorization);
        httpServletResponse.setStatus(302);
    }

    @RequestMapping(SPOTIFY_REDIRECT_PATH)
    public void spotifyRedirect(@RequestParam(value = "code", required = false) String spotifyCode,
        @RequestParam(value = "state", required = false) UUID state,
        @RequestParam(value = "error", required = false) String error,
        HttpServletResponse httpServletResponse) throws IOException {

        if (state == null || isBlank(spotifyCode)) {
            log.info("User failed to grant permission on Spotify. Received code {}, state {} with error {}", spotifyCode, state, error);
            httpServletResponse.sendRedirect("/error.html");
            return;
        }

        log.info("User has granted permission on Spotify. Received code {} for state {}", spotifyCode, state);
        spotifyClient.updateAuthToken(spotifyCode, state);
        httpServletResponse.sendRedirect("/success.html");
    }

    @PostMapping(value = "/slack/command", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String receiveSlackCommand(
        @RequestHeader("X-Slack-Request-Timestamp") Long timestamp,
        @RequestHeader("X-Slack-Signature") String signature,
        @RequestParam Map<String, String> fields,
        @RequestBody String bodyString) {

        log.trace("Received a slack command {}", bodyString);
        slackPollingClient.validateSignature(timestamp, signature, bodyString);
        String command = trimToEmpty(fields.get("text")).toLowerCase();
        String userId = trimToEmpty(fields.get("user_id"));
        //todo if no command, display modal, otherwise accept command
        if (PAUSE_COMMANDS.contains(command)) {
            log.debug("Pausing updates for user {}", userId);
            return slackPollingClient.pause(userId);
        }
        if (RESUME_COMMANDS.contains(command)) {
            log.debug("Resuming updates for user {}", userId);
            return slackPollingClient.resume(userId);
        }
        if (REMOVE_COMMANDS.contains(command)) {
            log.debug("Removing all data for user {}", userId);
            return slackPollingClient.purge(userId);
        }

        return "- `pause`/`stop` to temporarily pause status updates"
            + "\n- `unpause`/`play`/`resume` to resume status updates"
            + "\n- `purge`/`remove` to purge all user data. Fresh signup will be needed to use the app again";
    }

    @PostMapping(value = "/slack/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public String receiveSlackEvent(@RequestBody SlackEvent slackEvent) {

        log.debug("Received a slack event {}", slackEvent);

        return slackEvent.getChallenge();
    }

    @RequestMapping("/error")
    public void handleError(HttpServletResponse httpServletResponse) throws IOException {
        httpServletResponse.sendRedirect("/error.html");
    }

    @PostMapping(value = "/slack/modal", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void handleModalTrigger(@RequestParam(value = "trigger_id", required = false) String triggerId,
        @RequestParam("user_id") String userId) {

        SlackModalIn slackModalIn = new SlackModalIn();
        slackModalIn.setTriggerId(triggerId);
        try {
            slackModalIn.setView(slackInteractionClient.getModalViewTemplate());
        } catch (IOException e) {
            log.error("Failed to create modal view template", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR);
        }
        String response = notifyUser(slackModalIn, "open").getBody();
        log.trace("Received response: {}", response); //todo
    }

    public ResponseEntity<String> notifyUser(Object body, final String viewAction) {
        return RestHelper.builder()
                         .withBaseUrl("https://slack.com/api/views." + viewAction)
                         .withBearer("my_bearer")
                         .withContentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                         .withBody(body)
                         .post(restTemplate, String.class);
    }

    @PostMapping(value = "/slack/interaction", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void handleInteraction(@RequestParam("payload") SlackModalIn payload, @RequestParam("payload") String payloadRaw) {
        log.debug("Received interaction: {}", payloadRaw);
        String userId = getUserId(payload);
        //todo handle submission
        // persist user preferences
        if (PAYLOAD_TYPE_BLOCK_ACTIONS.equals(payload.getType())) {
            String userAction = getUserAction(payload);
            log.debug("User {} triggered {}", userId, userAction);
            if (payload.getActions() != null && ACTION_ID_EMOJI_ADD.equals(userAction)) {
                handleEmojiAdd(payload);
            } else if (payload.getActions() != null && ACTION_ID_EMOJI_REMOVE.equals(userAction)) {
                handleEmojiRemove(payload);
            }
        } else if (PAYLOAD_TYPE_SUBMISSION.equals(payload.getType())) {
            log.debug("User {} submitted the form", userId);
            boolean disableSync = getStateValue(payload, BLOCK_ID_SYNC_TOGGLE).getSelectedValues().isEmpty();
            List<String> spotifyItems = getStateValue(payload, BLOCK_ID_SPOTIFY_ITEMS).getSelectedValues();
            List<String> newEmojis = new ArrayList<>();
            for (Block block : payload.getView().getBlocks()) {
                if (BLOCK_ID_EMOJI_LIST.equals(block.getBlockId())) {
                    String userEmojis = (String) block.getElements().get(0).getText();
                    Arrays.stream(userEmojis.replace("Current list: ", "").split(", "))
                          .map(emoji -> emoji.split(":\\(")[0].replace(":", ""))
                          .forEach(newEmojis::add);
                }
            }
        }
    }

    private void handleEmojiAdd(SlackModalIn payload) {
        handleEmojiUpdate(payload, this::addEmojis);
    }

    private void handleEmojiRemove(SlackModalIn payload) {
        handleEmojiUpdate(payload, this::removeEmojis);
    }

    private void handleEmojiUpdate(SlackModalIn payload, BiFunction<String, List<String>, String> emojiUpdateFunction) {
        for (Block block : payload.getView().getBlocks()) {
            if (BLOCK_ID_EMOJI_LIST.equals(block.getBlockId())) {
                String currentEmojis = (String) block.getElements().get(0).getText();
                String newEmojiInput = getStateValue(payload, BLOCK_ID_EMOJI_INPUT).getValue();
                if (isBlank(newEmojiInput)) {
                    return;
                }
                List<String> newEmojis = Arrays.stream(newEmojiInput.split(","))
                                               .filter(StringUtils::isNotBlank)
                                               .map(emoji -> emoji.trim().replaceAll(":", ""))
                                               .collect(Collectors.toList());

                String updatedEmojiList = emojiUpdateFunction.apply(currentEmojis, newEmojis);
                block.getElements().get(0).setText(updatedEmojiList);
            } else if (BLOCK_ID_EMOJI_INPUT.equals(block.getBlockId())) {
                block.getElement().setActionId(null);
            }
        }
        SlackModalOut slackModal = new SlackModalOut();
        slackModal.setViewId(payload.getView().getId());
        slackModal.setHash(payload.getView().getHash());
        slackModal.setView(payload.getView());
        slackModal.getView().setHash(null);
        slackModal.getView().setId(null);
        slackModal.getView().setState(null);
        String response = notifyUser(slackModal, "update").getBody();//todo
        log.trace("Received modal update response: {}", response);
    }

    private StateValue getStateValue(SlackModalIn payload, String blockId) {
        return payload.getView()
                      .getState()
                      .getStateValues()
                      .get(blockId);
    }

    private String addEmojis(String currentEmojis, List<String> newEmojis) {
        String newEmojisMerged = newEmojis
            .stream()
            .filter(emoji -> validate(emoji, currentEmojis))
            .map(emoji -> String.format(EMOJI_LIST_FORMAT, emoji, emoji))
            .collect(Collectors.joining(", "));
        return trimToEmpty(currentEmojis).concat(", ").concat(newEmojisMerged);
    }

    private String removeEmojis(String currentEmojis, List<String> removedEmojis) {
        String updatedEmojis = currentEmojis;
        for (String removedEmoji : removedEmojis) {
            updatedEmojis = updatedEmojis.replace(String.format(EMOJI_LIST_FORMAT, removedEmoji, removedEmoji), "");
        }
        return trimToEmpty(updatedEmojis.replaceAll(" ,", ""));
    }

    private String getUserId(SlackModalIn payload) {
        return payload.getUser() != null ? payload.getUser().getId() : null;
    }

    private String getUserAction(SlackModalIn payload) {
        return Optional.ofNullable(payload)
                       .map(SlackModalIn::getActions)
                       .filter(not(CollectionUtils::isEmpty))
                       .map(actions -> actions.get(0))
                       .map(Action::getActionId)
                       .orElse(null);
    }

    private boolean validate(String newEmoji, String currentEmojis) {
        if (newEmoji.length() > 100) {
            //todo return error
        }
        return !currentEmojis.contains("`" + newEmoji + "`");
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(SlackModalIn.class, new SlackModalConverter());
    }
}
