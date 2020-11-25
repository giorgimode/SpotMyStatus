package com.giorgimode.SpotMyStatus.service;

import static com.giorgimode.SpotMyStatus.util.SpotConstants.BLOCK_ID_EMOJI_INPUT;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.BLOCK_ID_EMOJI_LIST;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.BLOCK_ID_HOURS_INPUT;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.BLOCK_ID_INVALID_HOURS;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.BLOCK_ID_PURGE;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.BLOCK_ID_SPOTIFY_DEVICES;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.BLOCK_ID_SPOTIFY_ITEMS;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.BLOCK_ID_SYNC_TOGGLE;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.DEFAULT_EMOJI_PLACEHOLDER;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.EMOJI_REGEX;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.PAYLOAD_TYPE_BLOCK_ACTIONS;
import static com.giorgimode.SpotMyStatus.util.SpotConstants.PAYLOAD_TYPE_SUBMISSION;
import static com.giorgimode.SpotMyStatus.util.SpotUtil.OBJECT_MAPPER;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import com.giorgimode.SpotMyStatus.common.SpotMyStatusProperties;
import com.giorgimode.SpotMyStatus.exceptions.UserNotFoundException;
import com.giorgimode.SpotMyStatus.model.CachedUser;
import com.giorgimode.SpotMyStatus.model.SpotifyItem;
import com.giorgimode.SpotMyStatus.model.modals.Accessory;
import com.giorgimode.SpotMyStatus.model.modals.Action;
import com.giorgimode.SpotMyStatus.model.modals.Block;
import com.giorgimode.SpotMyStatus.model.modals.Element;
import com.giorgimode.SpotMyStatus.model.modals.Option;
import com.giorgimode.SpotMyStatus.model.modals.SlackModalIn;
import com.giorgimode.SpotMyStatus.model.modals.SlackModalOut;
import com.giorgimode.SpotMyStatus.model.modals.SlackModalView;
import com.giorgimode.SpotMyStatus.model.modals.StateValue;
import com.giorgimode.SpotMyStatus.model.modals.Text;
import com.giorgimode.SpotMyStatus.persistence.UserRepository;
import com.giorgimode.SpotMyStatus.slack.SlackPollingClient;
import com.giorgimode.SpotMyStatus.spotify.SpotifyClient;
import com.giorgimode.SpotMyStatus.util.RestHelper;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.io.IOException;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Component
@Slf4j
public class UserInteractionService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SpotMyStatusProperties spotMyStatusProperties;

    @Autowired
    private LoadingCache<String, CachedUser> userCache;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private SlackPollingClient slackClient;

    @Autowired
    private SpotifyClient spotifyClient;

    @Value("classpath:templates/slack_modal_view_template.json")
    private Resource resourceFile;

    @Value("${slack_uri}")
    private String slackUri;

    @Value("${secret.slack.bot_token}")
    private String slackBotToken;

    public void invalidateAndNotifyUser(String userId) {
        slackClient.invalidateAndNotifyUser(userId);
    }

    public void handleTrigger(String userId, String triggerId) {
        CachedUser cachedUser = getCachedUser(userId);
        SlackModalView modalViewTemplate = getModalViewTemplate();
        SlackModalIn slackModalIn = new SlackModalIn();
        slackModalIn.setTriggerId(triggerId);
        modalViewTemplate.getBlocks().forEach(block -> {
            Accessory accessory = block.getAccessory();
            if (BLOCK_ID_SPOTIFY_ITEMS.equals(block.getBlockId())) {
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
            } else if (BLOCK_ID_EMOJI_LIST.equals(block.getBlockId())) {
                List<Option> emojiOptions = getUserEmojis(cachedUser)
                    .stream()
                    .map(emoji -> createOption(emoji, ":" + emoji + ":"))
                    .collect(toList());
                accessory.setOptions(emojiOptions);
                accessory.setInitialOptions(emojiOptions);
            } else if (BLOCK_ID_SYNC_TOGGLE.equals(block.getBlockId())) {
                if (cachedUser.isDisabled()) {
                    accessory.setInitialOptions(null);
                }
            } else if (BLOCK_ID_HOURS_INPUT.equals(block.getBlockId())) {
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
            } else if (BLOCK_ID_SPOTIFY_DEVICES.equals(block.getBlockId())) {
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
        });
        slackModalIn.setView(modalViewTemplate);
        String response = notifyUser(slackModalIn, "open").getBody();
        log.trace("Received response: {}", response); //todo return summary of changes instead
    }

    private SlackModalView getModalViewTemplate() {
        try {
            return OBJECT_MAPPER.readValue(resourceFile.getInputStream(), SlackModalView.class);
        } catch (IOException e) {
            log.error("Failed to create modal view template", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR);
        }
    }

    private Option createOption(String itemValue, String itemText) {
        Option option = new Option();
        option.setValue(itemValue);
        Text text = new Text();
        text.setType("plain_text");
        text.setText(itemText);
        option.setText(text);
        return option;
    }

    private List<String> getUserEmojis(CachedUser cachedUser) {
        if (cachedUser.getEmojis().isEmpty()) {
            return spotMyStatusProperties.getDefaultEmojis();
        }
        return cachedUser.getEmojis();
    }

    public ResponseEntity<String> notifyUser(Object body, final String viewAction) {
        return RestHelper.builder()
                         .withBaseUrl(slackUri + "/api/views." + viewAction) //todo
                         .withBearer(slackBotToken)
                         .withContentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                         .withBody(body)
                         .post(restTemplate, String.class);
    }

    public SlackModalOut handleUserInteraction(SlackModalIn payload) {
        String userId = getUserId(payload);
        CachedUser cachedUser = getCachedUser(userId);
        List<Block> blocks = payload.getView().getBlocks();
        if (PAYLOAD_TYPE_BLOCK_ACTIONS.equals(payload.getType())) {
            blocks.stream()
                  .filter(block -> BLOCK_ID_INVALID_HOURS.equals(block.getBlockId()))
                  .findAny()
                  .ifPresent(block -> block = null);

            getUserAction(payload)
                .ifPresent(userAction -> {
                    log.debug("User {} triggered {}", userId, userAction);
                    if (BLOCK_ID_EMOJI_INPUT.equals(userAction.getBlockId())) {
                        handleEmojiAdd(payload, userAction.getValue());
                    } else if (BLOCK_ID_PURGE.equals(userAction.getBlockId())) {
                        slackClient.purge(userId);
                    } else if (BLOCK_ID_HOURS_INPUT.equals(userAction.getBlockId())) {
                        String startHour = getStateValue(payload, BLOCK_ID_HOURS_INPUT).getStartHour();
                        String endHour = getStateValue(payload, BLOCK_ID_HOURS_INPUT).getEndHour();
                        SlackModalOut slackModal = createModalResponse(payload);
                        if (startHour != null && startHour.equals(endHour)) {
                            Block block = createWarningBlock();
                            for (int i = 0; i < blocks.size(); i++) {
                                if (BLOCK_ID_HOURS_INPUT.equals(blocks.get(i).getBlockId())) {
                                    blocks.add(i + 1, block);
                                }
                            }
                            String response = notifyUser(slackModal, "update").getBody();//todo
                            log.trace("Received warning update response: {}", response);
                        } else {
                            boolean removed = blocks.removeIf(block -> BLOCK_ID_INVALID_HOURS.equals(block.getBlockId()));
                            if (removed) {
                                String response = notifyUser(slackModal, "update").getBody();//todo
                                log.trace("Received warning update response: {}", response);
                            }
                        }
                    }
                });
        } else if (PAYLOAD_TYPE_SUBMISSION.equals(payload.getType())) {
            log.debug("User {} submitted the form", userId);
            boolean disableSync = getStateValue(payload, BLOCK_ID_SYNC_TOGGLE).getSelectedOptions().isEmpty();
            List<Option> spotifyItems = getStateValue(payload, BLOCK_ID_SPOTIFY_ITEMS).getSelectedOptions();
            List<Option> spotifyDevices = getStateValue(payload, BLOCK_ID_SPOTIFY_DEVICES).getSelectedOptions();
            for (int i = 0; i < blocks.size(); i++) {
                if (BLOCK_ID_HOURS_INPUT.equals(blocks.get(i).getBlockId())) {
                    String startHour = getStateValue(payload, BLOCK_ID_HOURS_INPUT).getStartHour();
                    String endHour = getStateValue(payload, BLOCK_ID_HOURS_INPUT).getEndHour();
                    if (startHour.equals(endHour)) {
                        Block block = createWarningBlock();
                        blocks.add(i + 1, block);
                        String response = "{ \"response_action\": \"update\", \"view\": { \"type\": \"modal\", \"title\": { \"type\": \"plain_text\", \"text\": \"Updated view\" }, \"blocks\": [ { \"type\": \"section\", \"text\": { \"type\": \"plain_text\", \"text\": \"I've changed and I'll never be the same. You must believe me.\" } } ] } }";
                        SlackModalOut modalResponse = createModalResponse(payload);
//                        modalResponse.getView().setSubmit(null);
                        modalResponse.setViewId(null);
                        modalResponse.setHash(null);
                        modalResponse.getView().setCallbackId(null);
                        modalResponse.setResponseAction("update");
                        return modalResponse;
                    }
                    updateSyncHours(cachedUser, startHour, endHour);
                } else if (BLOCK_ID_EMOJI_LIST.equals(blocks.get(i).getBlockId())) {
                    StateValue emojiStateValue = getStateValue(payload, BLOCK_ID_EMOJI_LIST);
                    if (isNotBlank(emojiStateValue.getType())) {
                        updateEmojis(cachedUser, emojiStateValue.getSelectedOptions());
                    } else {
                        updateEmojis(cachedUser, blocks.get(i).getAccessory().getInitialOptions());
                    }
                }
            }
            updateSpotifyItems(cachedUser, spotifyItems);
            updateSpotifyDevices(cachedUser, spotifyDevices);
            updateSync(userId, disableSync);

            userRepository.findById(cachedUser.getId()).ifPresent(user -> {
                user.setEmojis(String.join(",", cachedUser.getEmojis()));
                user.setSpotifyItems(cachedUser.getSpotifyItems().stream().map(SpotifyItem::title).collect(Collectors.joining(",")));
                user.setSyncFrom(cachedUser.getSyncStartHour());
                user.setSyncTo(cachedUser.getSyncEndHour());
                user.setSpotifyDevices(String.join(",", cachedUser.getSpotifyDeviceIds()));
                userRepository.save(user);
            });
        }
        return null;
    }

    private Block createWarningBlock() {
        Block block = new Block();
        block.setType("context");
        block.setBlockId(BLOCK_ID_INVALID_HOURS);
        Element element = new Element();
        element.setType("mrkdwn");
        element.setText(":warning: start and end time cannot identical");
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
            selectedEmojis.addAll(spotMyStatusProperties.getDefaultEmojis());
        }

        cachedUser.setEmojis(selectedEmojis);
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
        CachedUser cachedUser = userCache.getIfPresent(userId);
        if (cachedUser == null) {
            throw new UserNotFoundException("No user found in cache");
        }
        return cachedUser;
    }

    public void updateSpotifyItems(CachedUser cachedUser, List<Option> selectedSpotifyOptions) {
        List<String> spotifyItemsList = getOptionValues(selectedSpotifyOptions);
        List<SpotifyItem> spotifyItems = spotifyItemsList.stream().map(SpotifyItem::from).collect(toList());
        cachedUser.setSpotifyItems(spotifyItems);
    }

    public void updateSpotifyDevices(CachedUser cachedUser, List<Option> spotifyDevices) {
        List<String> spotifyDevicesList = getOptionValues(spotifyDevices);
        cachedUser.setSpotifyDeviceIds(spotifyDevicesList);
    }

    private List<String> getOptionValues(List<Option> options) {
        return options.stream().map(Option::getValue).collect(toList());
    }

    private void handleEmojiAdd(SlackModalIn payload, String newEmojiInput) {
        //todo validate(newEmojiInput),
        if (isBlank(newEmojiInput)) {
            return;
        }
        String validationError = "";
        if (newEmojiInput.length() > 100) {
            validationError = "Emoji cannot be longer than 100 characters";
        } else if (!StringUtils.strip(newEmojiInput, ":").matches(EMOJI_REGEX)) {
            validationError = "Emoji can only contain alphanumeric characters, - and _";
        }
        for (Block block : payload.getView().getBlocks()) {
            if (BLOCK_ID_EMOJI_LIST.equals(block.getBlockId())) {
                StateValue selectedEmojiBlock = getStateValue(payload, BLOCK_ID_EMOJI_LIST);
                List<Option> selectedOptions;
                if (isBlank(selectedEmojiBlock.getType())) {
                    // if state doesn't change in emoji list block, slack delivers empty block
                    // 'type' field should be present even if user removes all emojis.
                    // That's how we can differentiate actual user input from a no input and set previously set initial options
                    selectedOptions = block.getAccessory().getInitialOptions();
                } else {
                    selectedOptions = selectedEmojiBlock.getSelectedOptions();
                }

                block.getAccessory().setInitialOptions(selectedOptions);
                if (validationError.isEmpty()) {
                    updateEmojiList(newEmojiInput, block);
                }
                block.getAccessory().setActionId(String.valueOf(System.currentTimeMillis()));
            } else if (BLOCK_ID_EMOJI_INPUT.equals(block.getBlockId())) {
                block.getElement().getPlaceholder().setText(validationError.isEmpty() ? DEFAULT_EMOJI_PLACEHOLDER : validationError);
                block.getElement().setActionId(null);
            }
        }
        SlackModalOut slackModal = createModalResponse(payload);
        String response = notifyUser(slackModal, "update").getBody();//todo
//        log.trace("Received modal update response: {}", response);
    }

    private void updateEmojiList(String newEmojiInput, Block block) {
        Arrays.stream(newEmojiInput.split(","))
              .filter(StringUtils::isNotBlank)
              .map(emoji -> emoji.trim().replaceAll(":", ""))
              .map(emoji -> createOption(emoji, ":" + emoji + ":"))
              .forEach(emojiOption -> {
                  if (!block.getAccessory().getOptions().contains(emojiOption)) {
                      block.getAccessory().getOptions().add(emojiOption);
                  }
                  if (!block.getAccessory().getInitialOptions().contains(emojiOption)) {
                      block.getAccessory().getInitialOptions().add(emojiOption);
                  }
              });
    }

    SlackModalOut createModalResponse(SlackModalIn payload) {
        SlackModalOut slackModal = new SlackModalOut();
        slackModal.setViewId(payload.getView().getId());
        slackModal.setHash(payload.getView().getHash());
        slackModal.setView(payload.getView());
        slackModal.getView().setHash(null);
        slackModal.getView().setId(null);
        slackModal.getView().setState(null);
        return slackModal;
    }

    private StateValue getStateValue(SlackModalIn payload, String blockId) {
        return payload.getView()
                      .getState()
                      .getStateValues()
                      .get(blockId);
    }

    private String getUserId(SlackModalIn payload) {
        return payload.getUser() != null ? payload.getUser().getId() : null;
    }

    private Optional<Action> getUserAction(SlackModalIn payload) {
        return Optional.ofNullable(payload)
                       .map(SlackModalIn::getActions)
                       .filter(not(CollectionUtils::isEmpty))
                       .map(actions -> actions.get(0));
    }
}
