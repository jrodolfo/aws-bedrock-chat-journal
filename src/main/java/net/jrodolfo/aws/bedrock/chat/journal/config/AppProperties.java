package net.jrodolfo.aws.bedrock.chat.journal.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @Valid
    @NotNull
    private Aws aws = new Aws();

    @Valid
    @NotNull
    private Storage storage = new Storage();

    @Valid
    @NotNull
    private Limits limits = new Limits();

    @Valid
    @NotNull
    private Inference inference = new Inference();

    @Valid
    @NotNull
    private Logging logging = new Logging();

    public Aws getAws() {
        return aws;
    }

    public void setAws(Aws aws) {
        this.aws = aws;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Limits getLimits() {
        return limits;
    }

    public void setLimits(Limits limits) {
        this.limits = limits;
    }

    public Inference getInference() {
        return inference;
    }

    public void setInference(Inference inference) {
        this.inference = inference;
    }

    public Logging getLogging() {
        return logging;
    }

    public void setLogging(Logging logging) {
        this.logging = logging;
    }

    public static class Aws {

        @NotBlank(message = "app.aws.region must not be blank")
        private String region;

        @NotBlank(message = "app.aws.default-model-id must not be blank")
        private String defaultModelId;

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getDefaultModelId() {
            return defaultModelId;
        }

        public void setDefaultModelId(String defaultModelId) {
            this.defaultModelId = defaultModelId;
        }
    }

    public static class Storage {

        @NotBlank(message = "app.storage.sessions-directory must not be blank")
        private String sessionsDirectory;

        public String getSessionsDirectory() {
            return sessionsDirectory;
        }

        public void setSessionsDirectory(String sessionsDirectory) {
            this.sessionsDirectory = sessionsDirectory;
        }
    }

    public static class Limits {

        @Positive(message = "app.limits.max-messages-per-session must be greater than 0")
        private int maxMessagesPerSession = 100;

        public int getMaxMessagesPerSession() {
            return maxMessagesPerSession;
        }

        public void setMaxMessagesPerSession(int maxMessagesPerSession) {
            this.maxMessagesPerSession = maxMessagesPerSession;
        }
    }

    public static class Inference {

        @DecimalMin(value = "0.0", message = "app.inference.temperature must be between 0.0 and 1.0")
        @DecimalMax(value = "1.0", message = "app.inference.temperature must be between 0.0 and 1.0")
        private double temperature = 0.7;

        @DecimalMin(value = "0.0", message = "app.inference.top-p must be between 0.0 and 1.0")
        @DecimalMax(value = "1.0", message = "app.inference.top-p must be between 0.0 and 1.0")
        private double topP = 0.9;

        @Min(value = 1, message = "app.inference.max-tokens must be at least 1")
        private int maxTokens = 512;

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public double getTopP() {
            return topP;
        }

        public void setTopP(double topP) {
            this.topP = topP;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }

    public static class Logging {

        @NotNull
        private BedrockPayloadMode bedrockPayloadMode = BedrockPayloadMode.OFF;

        private boolean redactBedrockContent = true;

        public BedrockPayloadMode getBedrockPayloadMode() {
            return bedrockPayloadMode;
        }

        public void setBedrockPayloadMode(BedrockPayloadMode bedrockPayloadMode) {
            this.bedrockPayloadMode = bedrockPayloadMode;
        }

        public boolean isRedactBedrockContent() {
            return redactBedrockContent;
        }

        public void setRedactBedrockContent(boolean redactBedrockContent) {
            this.redactBedrockContent = redactBedrockContent;
        }
    }

    public enum BedrockPayloadMode {
        OFF,
        SUMMARY,
        RAW
    }
}
