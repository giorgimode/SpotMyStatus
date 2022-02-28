package com.giorgimode.spotmystatus.command;

import static com.giorgimode.spotmystatus.helpers.SpotUtil.baseUri;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isBlank;
import com.giorgimode.spotmystatus.service.UserInteractionService;
import com.giorgimode.spotmystatus.slack.SlackClient;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CommandHandler {

    private static final String SHA_256_ALGORITHM = "HmacSHA256";

    private final UserInteractionService userInteractionService;
    private final Map<String, Function<String, String>> COMMAND_MAP;
    private final String slackSigningSecret;
    private final boolean shouldVerifySignature;

    public CommandHandler(SlackClient slackClient,
        UserInteractionService userInteractionService,
        @Value("${secret.slack.signing_secret}") String slackSigningSecret,
        @Value("${signature_verification_enabled}") boolean shouldVerifySignature) {

        this.userInteractionService = userInteractionService;
        this.slackSigningSecret = slackSigningSecret;
        this.shouldVerifySignature = shouldVerifySignature;
        COMMAND_MAP = new ImmutableMap.Builder<String, Function<String, String>>()
            .put("pause", new PauseCommand(slackClient))
            .put("play", new PlayCommand(slackClient))
            .put("purge", new PurgeCommand(slackClient, userInteractionService))
            .put("links", new TrackUrlPrinterCommand(userInteractionService))
            .build();
    }

    public String handleCommand(CommandMetaData commandMetaData) {
        boolean isValidSignature = isValidSignature(commandMetaData.getTimestamp(), commandMetaData.getSignature(), commandMetaData.getBody());
        if (!isValidSignature) {
            log.error("Provided signature is not valid");
            return "Failed to validate signature. If the issue persists, please contact support at " + baseUri() + "/support";
        }
        if (userInteractionService.isUserMissing(commandMetaData.getUserId())) {
            return generateMissingUserResponse();
        }

        if (isBlank(commandMetaData.getCommand())) {
            log.debug("Generating modal view for user {}", commandMetaData.getUserId());
            userInteractionService.handleTrigger(commandMetaData.getUserId(), commandMetaData.getTriggerId());
            return null;
        }

        return COMMAND_MAP.getOrDefault(commandMetaData.getCommand(), (id) -> generateDefaultResponse()).apply(commandMetaData.getUserId());
    }

    private String generateDefaultResponse() {
        return "- `pause`/`play` to temporarily pause or resume status updates"
            + "\n- `purge` to purge all user data. Fresh signup will be needed to use the app again"
            + "\n- `links` to see what your teammates are listening to"
            + "\n- " + signupMessage();
    }

    private String generateMissingUserResponse() {
        return "User not found. Please sign up at " + baseUri()
            + "\nMake sure your Slack workspace admin has approved the app and try signing up again. "
            + "\nIf the issue persists, please contact support at " + baseUri() + "/support";
    }

    public boolean isValidSignature(Long timestamp, String signature, String bodyString) {
        if (!shouldVerifySignature) {
            return true;
        }

        return calculateSha256("v0:" + timestamp + ":" + bodyString)
            .map(hashedString -> ("v0=" + hashedString).equalsIgnoreCase(signature))
            .orElse(false);
    }

    private Optional<String> calculateSha256(String message) {
        try {
            Mac mac = Mac.getInstance(SHA_256_ALGORITHM);
            mac.init(new SecretKeySpec(slackSigningSecret.getBytes(UTF_8), SHA_256_ALGORITHM));
            byte[] hashedMessage = mac.doFinal(message.getBytes(UTF_8));
            return Optional.of(DatatypeConverter.printHexBinary(hashedMessage));
        } catch (Exception e) {
            log.error("Failed to calculate hmac-sha256", e);
            return Optional.empty();
        }
    }

    private String signupMessage() {
        return String.format("To sign up again visit the <%s|app home page>", baseUri());
    }
}
