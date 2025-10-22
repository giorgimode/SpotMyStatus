package com.giorgimode.spotmystatus.command;

import static com.giorgimode.spotmystatus.helpers.SpotUtil.baseUri;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Optional;
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

    private final String slackSigningSecret;
    private final boolean shouldVerifySignature;

    public CommandHandler(@Value("${secret.slack.signing_secret}") String slackSigningSecret,
                          @Value("${signature_verification_enabled}") boolean shouldVerifySignature) {

        this.slackSigningSecret = slackSigningSecret;
        this.shouldVerifySignature = shouldVerifySignature;
    }

    public String handleCommand(CommandMetaData commandMetaData) {
        boolean isValidSignature = isValidSignature(commandMetaData.getTimestamp(), commandMetaData.getSignature(), commandMetaData.getBody());
        if (!isValidSignature) {
            log.error("Provided signature is not valid");
            return "Failed to validate signature. If the issue persists, please contact support at " + baseUri() + "/support";
        }

        return signupMessage();
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
        return "Hey there! SpotMyStatus has a new home. Sign up at <https://spotmystatus.com/|spotmystatus.com>";
    }
}
