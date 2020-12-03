package com.giorgimode.spotmystatus.service;

import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_APP_URI;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_EMOJI_INPUT;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_EMOJI_LIST;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_FIRST_DIVIDER;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_HOURS_INPUT;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_INVALID_EMOJI;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_INVALID_HOURS;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_PURGE;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_SAVE_CHANGES;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_SPOTIFY_DEVICES;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_SPOTIFY_ITEMS;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_SUBMIT;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_SYNC_TOGGLE;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.EMOJI_REGEX;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.MODAL_FOOTER_MESSAGE;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.PAYLOAD_TYPE_BLOCK_ACTIONS;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.PAYLOAD_TYPE_SUBMISSION;
import static com.giorgimode.spotmystatus.helpers.SpotUtil.OBJECT_MAPPER;
import static com.giorgimode.spotmystatus.helpers.SpotUtil.baseUri;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import com.giorgimode.spotmystatus.helpers.PropertyVault;
import com.giorgimode.spotmystatus.helpers.SpotMyStatusProperties;
import com.giorgimode.spotmystatus.model.CachedUser;
import com.giorgimode.spotmystatus.model.SpotifyItem;
import com.giorgimode.spotmystatus.model.modals.Accessory;
import com.giorgimode.spotmystatus.model.modals.Action;
import com.giorgimode.spotmystatus.model.modals.Block;
import com.giorgimode.spotmystatus.model.modals.ConfirmDialog;
import com.giorgimode.spotmystatus.model.modals.Element;
import com.giorgimode.spotmystatus.model.modals.InteractionModal;
import com.giorgimode.spotmystatus.model.modals.InvocationModal;
import com.giorgimode.spotmystatus.model.modals.ModalView;
import com.giorgimode.spotmystatus.model.modals.Option;
import com.giorgimode.spotmystatus.model.modals.StateValue;
import com.giorgimode.spotmystatus.model.modals.Text;
import com.giorgimode.spotmystatus.persistence.UserRepository;
import com.giorgimode.spotmystatus.slack.SlackClient;
import com.giorgimode.spotmystatus.spotify.SpotifyClient;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.io.IOException;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
@Slf4j
public class UserInteractionService {

    private static final String SHA_256_ALGORITHM = "HmacSHA256";
    private static final String SLACK_VIEW_UPDATE_URI = "/api/views.update";
    private static final String PLAIN_TEXT = "plain_text";

    private final UserRepository userRepository;
    private final SpotMyStatusProperties spotMyStatusProperties;
    private final LoadingCache<String, CachedUser> userCache;
    private final SlackClient slackClient;
    private final SpotifyClient spotifyClient;
    private final PropertyVault propertyVault;

    @Value("classpath:templates/slack_modal_view_template.json")
    private Resource resourceFile;

    public UserInteractionService(UserRepository userRepository,
        SpotMyStatusProperties spotMyStatusProperties, LoadingCache<String, CachedUser> userCache,
        SlackClient slackClient, SpotifyClient spotifyClient, PropertyVault propertyVault) {

        this.userRepository = userRepository;
        this.spotMyStatusProperties = spotMyStatusProperties;
        this.userCache = userCache;
        this.slackClient = slackClient;
        this.spotifyClient = spotifyClient;
        this.propertyVault = propertyVault;
    }

    public boolean userExists(String userId) {
        return userCache.getIfPresent(userId) != null;
    }

    public void handleTrigger(String userId, String triggerId) {
        ModalView modalViewTemplate = createModalView(userId);
        InvocationModal invocationModal = new InvocationModal();
        invocationModal.setTriggerId(triggerId);
        invocationModal.setView(modalViewTemplate);
        String response = slackClient.notifyUser("/api/views.open", invocationModal);
        log.trace("Received response on trigger handle: {}", response);
    }

    private ModalView createModalView(String userId) {
        CachedUser cachedUser = getCachedUser(userId);
        ModalView modalViewTemplate = getModalViewTemplate();
        modalViewTemplate.getBlocks().forEach(block -> {
            if (BLOCK_ID_SPOTIFY_ITEMS.equals(block.getBlockId())) {
                prepareSpotifyItemsBlock(cachedUser, block);
            } else if (BLOCK_ID_EMOJI_LIST.equals(block.getBlockId())) {
                prepareEmojiListBlock(cachedUser, block.getElement());
            } else if (BLOCK_ID_SYNC_TOGGLE.equals(block.getBlockId())) {
                prepareSyncToggleBlock(cachedUser, block.getAccessory());
            } else if (BLOCK_ID_HOURS_INPUT.equals(block.getBlockId())) {
                prepareHoursBlock(cachedUser, block);
            } else if (BLOCK_ID_SPOTIFY_DEVICES.equals(block.getBlockId())) {
                prepareSpotifyDevicesBlock(cachedUser, block);
            } else if (BLOCK_ID_APP_URI.equals(block.getBlockId())) {
                block.getElements().get(0).setText(String.format(MODAL_FOOTER_MESSAGE, baseUri(spotMyStatusProperties.getRedirectUriScheme())));
            }
        });
        return modalViewTemplate;
    }

    public InteractionModal handleUserInteraction(InvocationModal payload) {
        if (PAYLOAD_TYPE_BLOCK_ACTIONS.equals(payload.getType())) {
            getUserAction(payload).ifPresent(userAction -> handleUserAction(payload, userAction));
        } else if (PAYLOAD_TYPE_SUBMISSION.equals(payload.getType())) {
            return handleSubmission(payload);
        }
        return null;
    }

    private void prepareSpotifyDevicesBlock(CachedUser cachedUser, Block block) {
        List<Option> spotifyDevices = spotifyClient.getSpotifyDevices(cachedUser)
                                                   .stream()
                                                   .map(device -> createOption(device.getId(), device.getName()))
                                                   .collect(toList());
        block.getElement().setOptions(spotifyDevices);
        List<Option> selectedDevices = spotifyDevices.stream()
                                                     .filter(device -> cachedUser.getSpotifyDeviceIds().contains(device.getValue()))
                                                     .collect(toList());
        block.getElement().setInitialOptions(selectedDevices.isEmpty() ? spotifyDevices : selectedDevices);
    }

    private void prepareHoursBlock(CachedUser cachedUser, Block block) {
        if (cachedUser.getSyncStartHour() != null && cachedUser.getSyncEndHour() != null) {
            Integer syncStartHour = cachedUser.getSyncStartHour();
            Integer setSyncEndHour = cachedUser.getSyncEndHour();
            OffsetTime offsetStartTime = LocalTime.of(syncStartHour / 100, syncStartHour % 100)
                                                  .atOffset(ZoneOffset.ofTotalSeconds(-cachedUser.getTimezoneOffsetSeconds()))
                                                  .withOffsetSameInstant(ZoneOffset.UTC);
            OffsetTime offsetEndTime = LocalTime.of(setSyncEndHour / 100, setSyncEndHour % 100)
                                                .atOffset(ZoneOffset.ofTotalSeconds(-cachedUser.getTimezoneOffsetSeconds()))
                                                .withOffsetSameInstant(ZoneOffset.UTC);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
            block.getElements().get(0).setInitialTime(offsetStartTime.format(formatter));
            block.getElements().get(1).setInitialTime(offsetEndTime.format(formatter));
        }
    }

    private void prepareSyncToggleBlock(CachedUser cachedUser, Accessory accessory) {
        if (cachedUser.isDisabled()) {
            accessory.setInitialOptions(null);
        }
    }

    private void prepareEmojiListBlock(CachedUser cachedUser, Element element) {
        List<Option> emojiOptions = getUserEmojis(cachedUser)
            .stream()
            .map(emoji -> createOption(emoji, ":" + emoji + ":"))
            .collect(toList());
        element.setOptions(emojiOptions);
        element.setInitialOptions(emojiOptions);
    }

    private void prepareSpotifyItemsBlock(CachedUser cachedUser, Block block) {
        List<Option> defaultItemOptions = spotMyStatusProperties.getDefaultSpotifyItems()
                                                                .entrySet()
                                                                .stream()
                                                                .map(entry -> createOption(entry.getKey(), entry.getValue()))
                                                                .collect(toList());
        List<Option> selectedItemsOptions = cachedUser.getSpotifyItems()
                                                      .stream()
                                                      .map(spotifyItem -> createOption(spotifyItem.title(),
                                                          spotMyStatusProperties.getDefaultSpotifyItems().get(spotifyItem.title())))
                                                      .collect(toList());
        block.getElement().setOptions(defaultItemOptions);
        if (selectedItemsOptions.isEmpty()) {
            block.getElement().setInitialOptions(defaultItemOptions);
        } else {
            block.getElement().setInitialOptions(selectedItemsOptions);
        }
    }

    private ModalView getModalViewTemplate() {
        try {
            return OBJECT_MAPPER.readValue(resourceFile.getInputStream(), ModalView.class);
        } catch (IOException e) {
            log.error("Failed to create modal view template", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR);
        }
    }

    private Option createOption(String itemValue, String itemText) {
        Option option = new Option();
        option.setValue(itemValue);
        Text text = new Text();
        text.setType(PLAIN_TEXT);
        text.setTextValue(itemText);
        option.setText(text);
        return option;
    }

    private List<String> getUserEmojis(CachedUser cachedUser) {
        if (cachedUser.getEmojis().isEmpty()) {
            return spotMyStatusProperties.getDefaultEmojis();
        }
        return cachedUser.getEmojis();
    }

    private InteractionModal handleSubmission(InvocationModal payload) {
        CachedUser cachedUser = getCachedUser(getUserId(payload));
        if (cachedUser == null) {
            return null;
        }
        List<Block> blocks = payload.getView().getBlocks();
        log.debug("User {} submitted the form", cachedUser.getId());
        for (Block block : blocks) {
            if (BLOCK_ID_HOURS_INPUT.equals(block.getBlockId())) {
                String startHour = getStateValue(payload, BLOCK_ID_HOURS_INPUT).getStartHour();
                String endHour = getStateValue(payload, BLOCK_ID_HOURS_INPUT).getEndHour();
                if (startHour.equals(endHour)) {
                    return returnModalWithWarning(payload);
                } else {
                    updateSyncHours(cachedUser, startHour, endHour);
                }
            } else if (BLOCK_ID_EMOJI_LIST.equals(block.getBlockId())) {
                StateValue emojiStateValue = getStateValue(payload, BLOCK_ID_EMOJI_LIST);
                if (isNotBlank(emojiStateValue.getType())) {
                    updateEmojis(cachedUser, emojiStateValue.getSelectedOptions());
                } else {
                    updateEmojis(cachedUser, block.getElement().getInitialOptions());
                }
            }
        }

        updateSpotifyItems(cachedUser, getStateValue(payload, BLOCK_ID_SYNC_TOGGLE).getSelectedOptions());
        updateSpotifyDevices(cachedUser, getStateValue(payload, BLOCK_ID_SYNC_TOGGLE).getSelectedOptions());
        updateSync(cachedUser.getId(), getStateValue(payload, BLOCK_ID_SYNC_TOGGLE).getSelectedOptions().isEmpty());
        persistChanges(cachedUser);
        return null;
    }

    private void persistChanges(CachedUser cachedUser) {
        userRepository.findById(cachedUser.getId()).ifPresent(user -> {
            user.setEmojis(trimToNull(String.join(",", cachedUser.getEmojis())));
            user.setSpotifyItems(trimToNull(cachedUser.getSpotifyItems().stream().map(SpotifyItem::title).collect(Collectors.joining(","))));
            user.setSyncFrom(cachedUser.getSyncStartHour());
            user.setSyncTo(cachedUser.getSyncEndHour());
            user.setSpotifyDevices(trimToNull(String.join(",", cachedUser.getSpotifyDeviceIds())));
            userRepository.save(user);
        });
    }

    private InteractionModal returnModalWithWarning(InvocationModal payload) {
        InteractionModal modalResponse = createModalResponse(payload);
        modalResponse.setViewId(null);
        modalResponse.setHash(null);
        modalResponse.getView().setCallbackId(null);
        modalResponse.setResponseAction("update");
        return modalResponse;
    }

    private void handleUserAction(InvocationModal payload, Action userAction) {
        String userId = getUserId(payload);
        List<Block> blocks = payload.getView().getBlocks();
        log.debug("User {} triggered {}", userId, userAction);
        if (BLOCK_ID_EMOJI_INPUT.equals(userAction.getBlockId())) {
            handleEmojiAdd(payload, userAction.getValue());
        } else if (BLOCK_ID_PURGE.equals(userAction.getBlockId())) {
            slackClient.purge(userId);
        } else if (BLOCK_ID_HOURS_INPUT.equals(userAction.getBlockId())) {
            handleHoursInput(payload, blocks);
        } else if (BLOCK_ID_SAVE_CHANGES.equals(userAction.getBlockId())) {
            //todo save changes or add warning
        }
    }

    private void handleHoursInput(InvocationModal payload, List<Block> blocks) {
        String startHour = getStateValue(payload, BLOCK_ID_HOURS_INPUT).getStartHour();
        String endHour = getStateValue(payload, BLOCK_ID_HOURS_INPUT).getEndHour();
        InteractionModal slackModal = createModalResponse(payload);
        if (startHour != null && startHour.equals(endHour)) {
            addWarningBlock(blocks, slackModal);
        } else {
            removeWarningBlock(blocks, slackModal);
        }
    }

    private void removeWarningBlock(List<Block> blocks, InteractionModal slackModal) {
        boolean removed = blocks.removeIf(block -> BLOCK_ID_INVALID_HOURS.equals(block.getBlockId()));
        if (removed) {
            String response = slackClient.notifyUser(SLACK_VIEW_UPDATE_URI, slackModal);
            log.trace("Received warning update response: {}", response);
        }
    }

    private void addWarningBlock(List<Block> blocks, InteractionModal slackModal) {
        Block block = createWarningBlock(BLOCK_ID_INVALID_HOURS, "start and end time cannot identical");
        for (int i = 0; i < blocks.size(); i++) {
            if (BLOCK_ID_HOURS_INPUT.equals(blocks.get(i).getBlockId())) {
                blocks.add(i + 1, block);
            }
        }
        String response = slackClient.notifyUser(SLACK_VIEW_UPDATE_URI, slackModal);
        log.trace("Received response on warning block: {}", response);
    }

    private Block createWarningBlock(String blockId, String warningMessage) {
        Block block = new Block();
        block.setType("context");
        block.setBlockId(blockId);
        Element element = new Element();
        element.setType("mrkdwn");
        element.setText(":warning: " + warningMessage);
        block.setElements(List.of(element));
        return block;
    }

    private void updateSync(String userId, boolean disableSync) {
        if (disableSync) {
            slackClient.pause(userId);
        } else {
            slackClient.resume(userId);
        }
    }

    public void updateEmojis(CachedUser cachedUser, List<Option> selectedEmojiOptions) {
        List<String> selectedEmojis = getOptionValues(selectedEmojiOptions);
        if (selectedEmojis.isEmpty()) {
            cachedUser.setEmojis(List.of());
        } else {
            cachedUser.setEmojis(selectedEmojis);
        }
    }


    private void updateSyncHours(CachedUser cachedUser, String startHour, String endHour) {
        ZoneOffset offset = ZoneOffset.ofTotalSeconds(cachedUser.getTimezoneOffsetSeconds());
        OffsetTime startTime = LocalTime.parse(startHour, DateTimeFormatter.ISO_LOCAL_TIME)
                                        .atOffset(offset)
                                        .withOffsetSameInstant(ZoneOffset.UTC);
        int offsetStartTime = startTime.getHour() * 100 + startTime.getMinute();
        OffsetTime endTime = LocalTime.parse(endHour, DateTimeFormatter.ISO_LOCAL_TIME)
                                      .atOffset(offset)
                                      .withOffsetSameInstant(ZoneOffset.UTC);
        int offsetEndHour = endTime.getHour() * 100 + endTime.getMinute();
        cachedUser.setSyncStartHour(offsetStartTime);
        cachedUser.setSyncEndHour(offsetEndHour);
    }

    private CachedUser getCachedUser(String userId) {
        return userCache.getIfPresent(userId);
    }

    public void updateSpotifyItems(CachedUser cachedUser, List<Option> selectedSpotifyItems) {
        if (selectedSpotifyItems.isEmpty()) {
            cachedUser.setSpotifyItems(List.of());
        } else {
            Collection<String> spotifyItemsList = getOptionValues(selectedSpotifyItems);
            List<SpotifyItem> spotifyItems = spotifyItemsList.stream().map(SpotifyItem::from).collect(toList());
            cachedUser.setSpotifyItems(spotifyItems);
        }
    }

    public void updateSpotifyDevices(CachedUser cachedUser, List<Option> spotifyDevices) {
        if (spotifyDevices.isEmpty()) {
            cachedUser.setSpotifyDeviceIds(List.of());
        } else {
            List<String> spotifyDevicesList = getOptionValues(spotifyDevices);
            cachedUser.setSpotifyDeviceIds(spotifyDevicesList);
        }
    }

    private List<String> getOptionValues(List<Option> options) {
        return options.stream().map(Option::getValue).collect(toList());
    }

    private void handleEmojiAdd(InvocationModal payload, String newEmojiInput) {
        if (isBlank(newEmojiInput)) {
            return;
        }
        Optional<String> validationError = getValidationError(newEmojiInput);
        List<Block> blocks = payload.getView().getBlocks();
        blocks.removeIf(warningBlock -> BLOCK_ID_INVALID_EMOJI.equals(warningBlock.getBlockId()));
        for (int i = 0, blocksSize = blocks.size(); i < blocksSize; i++) {
            Block block = blocks.get(i);
            if (BLOCK_ID_EMOJI_LIST.equals(block.getBlockId())) {
                validateAndAddEmoji(payload, newEmojiInput, validationError, block);
            } else if (BLOCK_ID_EMOJI_INPUT.equals(block.getBlockId())) {
                if (validationError.isPresent()) {
                    Block warningBlock = createWarningBlock(BLOCK_ID_INVALID_EMOJI, validationError.get());
                    blocks.add(i + 1, warningBlock);
                    break;
                } else {
                    // resetting action id forces Slack to recreate the element making it clean
                    block.getElement().setActionId(null);
                }
            }
        }
        InteractionModal slackModal = createModalResponse(payload);
        String response = slackClient.notifyUser(SLACK_VIEW_UPDATE_URI, slackModal);
        log.trace("Received response on emoji add: {}", response);
    }

    private void validateAndAddEmoji(InvocationModal payload, String newEmojiInput, Optional<String> validationError, Block block) {
        StateValue selectedEmojiBlock = getStateValue(payload, BLOCK_ID_EMOJI_LIST);
        List<Option> selectedOptions;
        if (isBlank(selectedEmojiBlock.getType())) {
            // if state doesn't change in emoji list block, slack delivers empty block
            // 'type' field should be present even if user removes all emojis.
            // That's how we can differentiate user removing all emojis from a no input and set previously present initial options
            selectedOptions = block.getElement().getInitialOptions();
        } else {
            selectedOptions = selectedEmojiBlock.getSelectedOptions();
        }

        block.getElement().setInitialOptions(selectedOptions);
        if (validationError.isEmpty()) {
            updateEmojiList(newEmojiInput, block);
        }
        // resetting action id forces Slack to recreate the element
        block.getElement().setActionId(null);
    }

    Optional<String> getValidationError(String newEmojiInput) {
        if (newEmojiInput.length() > 100) {
            return Optional.of("Emoji cannot be longer than 100 characters");
        } else if (!StringUtils.strip(newEmojiInput, ":").matches(EMOJI_REGEX)) {
            return Optional.of("Emoji can only contain alphanumeric characters, - and _");
        }
        return Optional.empty();
    }

    private void updateEmojiList(String newEmojiInput, Block block) {
        Arrays.stream(newEmojiInput.split(","))
              .filter(StringUtils::isNotBlank)
              .map(emoji -> emoji.trim().replace(":", ""))
              .map(emoji -> createOption(emoji, ":" + emoji + ":"))
              .forEach(emojiOption -> {
                  if (!block.getElement().getOptions().contains(emojiOption)) {
                      block.getElement().getOptions().add(emojiOption);
                  }
                  if (!block.getElement().getInitialOptions().contains(emojiOption)) {
                      block.getElement().getInitialOptions().add(emojiOption);
                  }
              });
    }

    InteractionModal createModalResponse(InvocationModal payload) {
        InteractionModal slackModal = new InteractionModal();
        slackModal.setViewId(payload.getView().getId());
        slackModal.setHash(payload.getView().getHash());
        slackModal.setView(payload.getView());
        slackModal.getView().setHash(null);
        slackModal.getView().setId(null);
        slackModal.getView().setState(null);
        return slackModal;
    }

    private StateValue getStateValue(InvocationModal payload, String blockId) {
        return payload.getView()
                      .getState()
                      .getStateValues()
                      .get(blockId);
    }

    private String getUserId(InvocationModal payload) {
        return payload.getUser() != null ? payload.getUser().getId() : null;
    }

    private Optional<Action> getUserAction(InvocationModal payload) {
        return Optional.ofNullable(payload)
                       .map(InvocationModal::getActions)
                       .filter(not(CollectionUtils::isEmpty))
                       .map(actions -> actions.get(0));
    }

    public void validateSignature(Long timestamp, String signature, String bodyString) {
        boolean isValid = calculateSha256("v0:" + timestamp + ":" + bodyString)
            .map(hashedString -> ("v0=" + hashedString).equalsIgnoreCase(signature))
            .orElse(false);

        if (isValid) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private Optional<String> calculateSha256(String message) {
        try {
            Mac mac = Mac.getInstance(SHA_256_ALGORITHM);
            mac.init(new SecretKeySpec(propertyVault.getSlack().getSigningSecret().getBytes(UTF_8), SHA_256_ALGORITHM));
            return Optional.of(DatatypeConverter.printHexBinary(mac.doFinal(message.getBytes(UTF_8))));
        } catch (Exception e) {
            log.error("Failed to calculate hmac-sha256", e);
            return Optional.empty();
        }
    }

    public String pause(String userId) {
        return slackClient.pause(userId);
    }

    public String resume(String userId) {
        return slackClient.resume(userId);
    }

    public String purge(String userId) {
        return slackClient.purge(userId);
    }

    public void updateHomeTab(String userId) {
        //todo add view if user is not in the system
        List<Block> blocks = createModalView(userId).getBlocks();
        updateBlocks(blocks);
        ModalView modalView = new ModalView();
        modalView.setBlocks(blocks);
        modalView.setType("home");
        InteractionModal homeModal = new InteractionModal();
        homeModal.setUserId(userId);
        homeModal.setView(modalView);
        String response = slackClient.notifyUser("/api/views.publish", homeModal);
        log.trace("Slack returned response when updating home tab {}", response);
    }

    private void updateBlocks(List<Block> blocks) {
        for (int i = 0; i < blocks.size(); i++) {
            if (BLOCK_ID_FIRST_DIVIDER.equals(blocks.get(i).getBlockId())) {
                addSubmitButton(blocks, i);
            } else if (BLOCK_ID_SPOTIFY_ITEMS.equals(blocks.get(i).getBlockId())) {
                Text label = blocks.get(i).getLabel();
                label.setTextValue(label.getTextValue() + ". All items will be included if none selected");
            } else if (BLOCK_ID_EMOJI_LIST.equals(blocks.get(i).getBlockId())) {
                blocks.get(i).getElement().getPlaceholder().setTextValue("Your emojis. Default emojis will be set if none selected");
            } else if (BLOCK_ID_SPOTIFY_DEVICES.equals(blocks.get(i).getBlockId())) {
                blocks.get(i).getElement().getPlaceholder().setTextValue("Select devices. All devices will be included if none selected");
            }
        }
    }

    private void addSubmitButton(List<Block> blocks, int index) {
        Block saveBlock = new Block();
        saveBlock.setType("actions");
        saveBlock.setBlockId(BLOCK_ID_SUBMIT);
        Element button = new Element();
        button.setType("button");
        button.setStyle("primary");
        Text buttonText = new Text();
        buttonText.setType(PLAIN_TEXT);
        buttonText.setTextValue("Save Changes");
        ConfirmDialog confirmDialog = new ConfirmDialog();
        Text confirmText = new Text();
        confirmText.setType(PLAIN_TEXT);
        confirmText.setTextValue("Would you like to submit changes?");
        confirmDialog.setText(confirmText);
        Text confirmButtonText = new Text();
        confirmButtonText.setType(PLAIN_TEXT);
        confirmButtonText.setTextValue("Submit");
        confirmDialog.setConfirm(confirmButtonText);
        button.setConfirm(confirmDialog);
        button.setText(buttonText);
        saveBlock.setElements(List.of(button));
        blocks.add(index + 1, saveBlock);
        Block divider = new Block();
        divider.setType("divider");
        blocks.add(index + 2, divider);
    }
}
