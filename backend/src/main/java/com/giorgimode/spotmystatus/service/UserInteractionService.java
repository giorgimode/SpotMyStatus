package com.giorgimode.spotmystatus.service;

import static com.giorgimode.spotmystatus.helpers.SpotConstants.ALL_DEVICES_ALLOWED_TEXT;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.ALL_DEVICES_ALLOWED_VALUE;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.ALL_DEVICES_OFFLINE_VALUE;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_APP_URI;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_EMOJI_INPUT;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_EMOJI_LIST;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_FIRST_DIVIDER;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_HOURS_INPUT;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_INVALID_EMOJI;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_INVALID_HOURS;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_PURGE;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_SPOTIFY_DEVICES;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_SPOTIFY_ITEMS;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_SPOTIFY_LINKS;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_SUBMIT;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.BLOCK_ID_SYNC_TOGGLE;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.EMOJI_REGEX;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.MODAL_FOOTER_MESSAGE;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.PAYLOAD_TYPE_BLOCK_ACTIONS;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.PAYLOAD_TYPE_SUBMISSION;
import static com.giorgimode.spotmystatus.helpers.SpotUtil.OBJECT_MAPPER;
import static com.giorgimode.spotmystatus.helpers.SpotUtil.baseUri;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.util.CollectionUtils.isEmpty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.giorgimode.spotmystatus.helpers.SpotMyStatusProperties;
import com.giorgimode.spotmystatus.model.CachedUser;
import com.giorgimode.spotmystatus.model.SpotifyCurrentItem;
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
import com.giorgimode.spotmystatus.persistence.User;
import com.giorgimode.spotmystatus.persistence.UserRepository;
import com.giorgimode.spotmystatus.slack.SlackClient;
import com.giorgimode.spotmystatus.spotify.SpotifyClient;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.io.IOException;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
@Slf4j
public class UserInteractionService {

    public static final String SLACK_VIEW_UPDATE_URI = "/api/views.update";
    public static final String SLACK_VIEW_OPEN_URI = "/api/views.open";
    public static final String SLACK_VIEW_PUBLISH_URI = "/api/views.publish";
    public static final String SLACK_VIEW_PUSH_URI = "/api/views.push";
    static final String NO_TRACK_WARNING_MESSAGE = "Currently none of your teammates are listening to anything";
    private static final String TEXT_TYPE_PLAIN = "plain_text";
    private static final String TEXT_TYPE_MARKDOWN = "mrkdwn";

    private final UserRepository userRepository;
    private final SpotMyStatusProperties spotMyStatusProperties;
    private final LoadingCache<String, CachedUser> userCache;
    private final SlackClient slackClient;
    private final SpotifyClient spotifyClient;

    @Value("classpath:templates/slack_modal_view_template.json")
    private Resource resourceFile;

    public UserInteractionService(UserRepository userRepository,
        SpotMyStatusProperties spotMyStatusProperties, LoadingCache<String, CachedUser> userCache,
        SlackClient slackClient, SpotifyClient spotifyClient) {

        this.userRepository = userRepository;
        this.spotMyStatusProperties = spotMyStatusProperties;
        this.userCache = userCache;
        this.slackClient = slackClient;
        this.spotifyClient = spotifyClient;
    }

    public boolean isUserMissing(String userId) {
        return userCache.getIfPresent(userId) == null;
    }

    public void handleTrigger(String userId, String triggerId) {
        ModalView modalViewTemplate = createModalView(userId);
        InvocationModal invocationModal = new InvocationModal();
        invocationModal.setTriggerId(triggerId);
        invocationModal.setView(modalViewTemplate);
        String response = slackClient.notifyUser(SLACK_VIEW_OPEN_URI, invocationModal, userId);
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
        Option allDevicesOption = createOption(ALL_DEVICES_ALLOWED_VALUE, ALL_DEVICES_ALLOWED_TEXT);
        if (spotifyDevices.isEmpty()) {
            block.getElement().getPlaceholder().setTextValue("All your Spotify devices are offline");
            block.getElement().setOptions(List.of(allDevicesOption));
        } else {
            List<Option> spotifyDevicesWithAnyDeviceOption = new ArrayList<>(spotifyDevices);
            spotifyDevicesWithAnyDeviceOption.add(allDevicesOption);
            block.getElement().setOptions(spotifyDevicesWithAnyDeviceOption);
            List<Option> selectedDevices = spotifyDevices.stream()
                                                         .filter(device -> cachedUser.getSpotifyDeviceIds().contains(device.getValue()))
                                                         .collect(toList());
            List<Option> initialOptions = isEmpty(selectedDevices) ? List.of(allDevicesOption) : selectedDevices;
            block.getElement().setInitialOptions(initialOptions);
        }
    }

    private void prepareSyncToggleBlock(CachedUser cachedUser, Accessory accessory) {
        if (cachedUser.isDisabled()) {
            accessory.setInitialOptions(null);
        }
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
        if (isEmpty(selectedItemsOptions)) {
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
        text.setType(TEXT_TYPE_PLAIN);
        text.setTextValue(itemText);
        option.setText(text);
        return option;
    }

    private List<String> getUserEmojis(CachedUser cachedUser) {
        if (isEmpty(cachedUser.getEmojis())) {
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

        updateSpotifyItems(cachedUser, getStateValue(payload, BLOCK_ID_SPOTIFY_ITEMS).getSelectedOptions());
        updateSpotifyDevices(cachedUser, getStateValue(payload, BLOCK_ID_SPOTIFY_DEVICES).getSelectedOptions());
        updateSync(cachedUser.getId(), getStateValue(payload, BLOCK_ID_SYNC_TOGGLE).getSelectedOptions().isEmpty());
        persistChanges(cachedUser);
        updateHomeTab(cachedUser.getId());
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
            purge(userId);
        } else if (BLOCK_ID_HOURS_INPUT.equals(userAction.getBlockId())) {
            handleHoursInput(payload, blocks);
        } else if (BLOCK_ID_SPOTIFY_LINKS.equals(userAction.getBlockId())) {
            handleSpotifyLinks(payload, userId);
        } else if (BLOCK_ID_SUBMIT.equals(userAction.getBlockId())) {
            handleSubmission(payload);
        }
    }

    private void handleHoursInput(InvocationModal payload, List<Block> blocks) {
        String startHour = getStateValue(payload, BLOCK_ID_HOURS_INPUT).getStartHour();
        String endHour = getStateValue(payload, BLOCK_ID_HOURS_INPUT).getEndHour();
        InteractionModal slackModal = createModalResponse(payload);
        if (startHour != null && startHour.equals(endHour)) {
            addWarningBlock(blocks, slackModal, getUserId(payload));
        } else {
            removeWarningBlock(blocks, slackModal, getUserId(payload));
        }
    }

    private void removeWarningBlock(List<Block> blocks, InteractionModal slackModal, String userId) {
        boolean removed = blocks.removeIf(block -> BLOCK_ID_INVALID_HOURS.equals(block.getBlockId()));
        if (removed) {
            String response = slackClient.notifyUser(SLACK_VIEW_UPDATE_URI, slackModal, userId);
            log.trace("Received warning update response: {}", response);
        }
    }

    private void addWarningBlock(List<Block> blocks, InteractionModal slackModal, String userId) {
        Block block = createWarningBlock(BLOCK_ID_INVALID_HOURS, "start and end time cannot identical");
        for (int i = 0; i < blocks.size(); i++) {
            if (BLOCK_ID_HOURS_INPUT.equals(blocks.get(i).getBlockId())) {
                blocks.add(i + 1, block);
            }
        }
        String response = slackClient.notifyUser(SLACK_VIEW_UPDATE_URI, slackModal, userId);
        log.trace("Received response on warning block: {}", response);
    }

    private Block createWarningBlock(String blockId, String warningMessage) {
        Block block = new Block();
        block.setType("context");
        block.setBlockId(blockId);
        Element element = new Element();
        element.setType(TEXT_TYPE_MARKDOWN);
        element.setText(":warning: " + warningMessage);
        block.setElements(List.of(element));
        return block;
    }

    private void handleSpotifyLinks(InvocationModal payload, String userId) {
        InteractionModal slackModal = createSpotifyLinksView(payload, userId);
        String slackEndpoint = "home".equals(slackModal.getView().getType()) ? SLACK_VIEW_PUBLISH_URI : SLACK_VIEW_PUSH_URI;
        String response = slackClient.notifyUser(slackEndpoint, slackModal, userId);
        log.trace("Received response on spotify links block: {}", response);
    }

    private InteractionModal createSpotifyLinksView(InvocationModal payload, String userId) {
        InteractionModal slackModal = new InteractionModal();
        ModalView currentTracksView = getCurrentTracksView(userId);
        Text title = new Text();
        title.setType(TEXT_TYPE_PLAIN);
        title.setTextValue("Current tracks");
        currentTracksView.setTitle(title);
        currentTracksView.setType(payload.getView().getType());
        slackModal.setTriggerId(payload.getTriggerId());
        slackModal.setView(currentTracksView);
        slackModal.setUserId(userId);
        return slackModal;
    }

    private void updateSync(String userId, boolean disableSync) {
        if (disableSync) {
            slackClient.pause(userId);
        } else {
            slackClient.resume(userId);
        }
    }

    private void updateEmojis(CachedUser cachedUser, List<Option> selectedEmojiOptions) {
        List<String> selectedEmojis = getOptionValues(selectedEmojiOptions);
        if (isEmpty(selectedEmojis)) {
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

    private void updateSpotifyItems(CachedUser cachedUser, List<Option> selectedSpotifyItems) {
        if (isEmpty(selectedSpotifyItems)) {
            cachedUser.setSpotifyItems(List.of());
        } else {
            Collection<String> spotifyItemsList = getOptionValues(selectedSpotifyItems);
            List<SpotifyItem> spotifyItems = spotifyItemsList.stream().map(SpotifyItem::from).collect(toList());
            cachedUser.setSpotifyItems(spotifyItems);
        }
    }

    private void updateSpotifyDevices(CachedUser cachedUser, List<Option> spotifyDevices) {
        List<String> deviceValues = spotifyDevices == null ? List.of() : spotifyDevices.stream()
                                                                                       .map(Option::getValue)
                                                                                       .filter(not(ALL_DEVICES_OFFLINE_VALUE::equals))
                                                                                       .collect(toList());

        if (deviceValues.contains(ALL_DEVICES_ALLOWED_VALUE)) {
            cachedUser.setSpotifyDeviceIds(List.of());
        } else {
            cachedUser.setSpotifyDeviceIds(deviceValues);
        }
    }

    private List<String> getOptionValues(List<Option> options) {
        return options.stream().map(Option::getValue).collect(toList());
    }

    private void handleEmojiAdd(InvocationModal payload, String newEmojiInput) {
        List<String> emojiList = Arrays.stream(newEmojiInput.split(","))
                                       .filter(StringUtils::isNotBlank)
                                       .map(String::trim)
                                       .collect(toList());
        if (isEmpty(emojiList)) {
            return;
        }
        List<String> validationErrors = emojiList.stream().map(this::getValidationError).flatMap(Optional::stream).collect(toList());
        List<Block> blocks = payload.getView().getBlocks();
        blocks.removeIf(warningBlock -> BLOCK_ID_INVALID_EMOJI.equals(warningBlock.getBlockId()));
        for (int i = 0, blocksSize = blocks.size(); i < blocksSize; i++) {
            Block block = blocks.get(i);
            if (BLOCK_ID_EMOJI_LIST.equals(block.getBlockId())) {
                validateAndAddEmoji(payload, emojiList, validationErrors, block);
            } else if (BLOCK_ID_EMOJI_INPUT.equals(block.getBlockId())) {
                if (!validationErrors.isEmpty()) {
                    Block warningBlock = createWarningBlock(BLOCK_ID_INVALID_EMOJI, validationErrors.get(0));
                    blocks.add(i + 1, warningBlock);
                    break;
                } else {
                    // resetting action id forces Slack to recreate the element making it clean
                    block.getElement().setActionId(null);
                }
            }
        }
        InteractionModal slackModal = createModalResponse(payload);
        String response = slackClient.notifyUser(SLACK_VIEW_UPDATE_URI, slackModal, getUserId(payload));
        log.trace("Received response on emoji add: {}", response);
    }

    private void validateAndAddEmoji(InvocationModal payload, List<String> newEmojis, List<String> validationErrors, Block block) {
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
        if (validationErrors.isEmpty()) {
            updateEmojiList(newEmojis, block);
        }
        // resetting action id forces Slack to recreate the element
        block.getElement().setActionId(null);
    }

    private Optional<String> getValidationError(String newEmojiInput) {
        if (newEmojiInput.length() > 100) {
            return Optional.of("Emoji cannot be longer than 100 characters");
        } else if (!StringUtils.strip(newEmojiInput, ":").matches(EMOJI_REGEX)) {
            return Optional.of("Emoji can only contain alphanumeric characters, - and _");
        }
        return Optional.empty();
    }

    private void updateEmojiList(List<String> newEmojis, Block block) {
        newEmojis.stream()
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

    private InteractionModal createModalResponse(InvocationModal payload) {
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

    private void purge(String userId) {
        updateHomeTabForMissingUser(userId);
        slackClient.purge(userId);
    }

    public void updateHomeTabForMissingUser(String userId) {
        try {
            InteractionModal homeModal = new InteractionModal();
            homeModal.setUserId(userId);
            ModalView modalView = new ModalView();
            modalView.setType("home");
            homeModal.setView(modalView);
            Block noUserHeader = createHeaderForMissingUser();
            Block signupBlock = createSignupBlockForMissingUser();
            modalView.setBlocks(List.of(noUserHeader, signupBlock));

            String response = slackClient.notifyUser(SLACK_VIEW_PUBLISH_URI, homeModal, userId);
            log.trace("Slack returned response when updating home tab {}", response);
        } catch (Exception e) {
            log.error("Failed to update home tab");
        }
    }

    public void updateHomeTab(String userId) {
        if (isUserMissing(userId)) {
            log.trace("Skipping updating home tab. User {} not found", userId);
        } else {
            InteractionModal homeModal = new InteractionModal();
            homeModal.setUserId(userId);
            ModalView modalView = new ModalView();
            modalView.setType("home");
            homeModal.setView(modalView);
            List<Block> blocks = createModalView(userId).getBlocks();
            updateBlocks(blocks);
            modalView.setBlocks(blocks);
            String response = slackClient.notifyUser(SLACK_VIEW_PUBLISH_URI, homeModal, userId);
            log.trace("Slack returned response when updating home tab {}", response);
        }
    }

    private Block createSignupBlockForMissingUser() {
        Block signupBlock = new Block();
        signupBlock.setType("section");
        Text signupText = new Text();
        signupText.setType(TEXT_TYPE_MARKDOWN);
        signupText.setTextValue(String.format("You can sign up <%s|here>", baseUri(spotMyStatusProperties.getRedirectUriScheme()) + "/api/start"));
        signupBlock.setText(signupText);
        return signupBlock;
    }

    private Block createHeaderForMissingUser() {
        Block noUserBlock = new Block();
        noUserBlock.setType("header");
        Text text = new Text();
        text.setTextValue(":no_entry_sign: User not found");
        text.setType(TEXT_TYPE_PLAIN);
        text.setEmoji(true);
        noUserBlock.setText(text);
        return noUserBlock;
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
                Text devicesPlaceholder = blocks.get(i).getElement().getPlaceholder();
                if (isBlank(devicesPlaceholder.getTextValue())) {
                    devicesPlaceholder.setTextValue("Select devices. All devices will be included if none selected");
                }
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
        buttonText.setType(TEXT_TYPE_PLAIN);
        buttonText.setTextValue("Save Changes");
        ConfirmDialog confirmDialog = new ConfirmDialog();
        Text confirmText = new Text();
        confirmText.setType(TEXT_TYPE_PLAIN);
        confirmText.setTextValue("Would you like to submit changes?");
        confirmDialog.setText(confirmText);
        Text confirmButtonText = new Text();
        confirmButtonText.setType(TEXT_TYPE_PLAIN);
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

    public String getCurrentTracksMessage(String userId) {
        ModalView currentTracksView = getCurrentTracksView(userId);
        return safeWrite(currentTracksView).orElse("Failed to fetch the tracks");
    }

    private ModalView getCurrentTracksView(String userId) {
        ModalView trackMessage = new ModalView();
        CachedUser cachedUser = getCachedUser(userId);
        List<User> teammates = userRepository.findAllByTeamId(cachedUser.getTeamId());
        List<Block> trackBlocks = teammates.stream()
                                       .map(teammate -> getCachedUser(teammate.getId()))
                                       .filter(Objects::nonNull)
                                       .filter(slackClient::isUserLive)
                                       .map(spotifyClient::getCurrentLiveTrack)
                                       .flatMap(Optional::stream)
                                       .map(this::buildSpotifyTracksMessage)
                                       .collect(toList());
        trackMessage.setBlocks(trackBlocks.isEmpty() ? createEmptyLinksBlock() : trackBlocks);
        return trackMessage;
    }

    private List<Block> createEmptyLinksBlock() {
        Block trackBlock = new Block();
        trackBlock.setType("section");
        Text titleText = new Text();
        titleText.setType(TEXT_TYPE_MARKDOWN);
        titleText.setTextValue(NO_TRACK_WARNING_MESSAGE);
        trackBlock.setText(titleText);
        return List.of(trackBlock);
    }

    private Optional<String> safeWrite(ModalView trackMessage) {
        try {
            return Optional.of(OBJECT_MAPPER.writeValueAsString(trackMessage));
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse class", e);
            return Optional.empty();
        }
    }

    private Block buildSpotifyTracksMessage(SpotifyCurrentItem spotifyCurrentItem) {
        Block trackBlock = new Block();
        trackBlock.setType("section");
        Text titleText = new Text();
        titleText.setType(TEXT_TYPE_MARKDOWN);
        titleText.setTextValue(String.format("<%s|%s>", spotifyCurrentItem.getTrackUrl(), spotifyCurrentItem.generateFullTitle(150)));
        trackBlock.setText(titleText);
        Accessory imageAccessory = new Accessory();
        imageAccessory.setType("image");
        imageAccessory.setImageUrl(spotifyCurrentItem.getImageUrl());
        imageAccessory.setAltText("Album Art");
        trackBlock.setAccessory(imageAccessory);
        return trackBlock;
    }
}
