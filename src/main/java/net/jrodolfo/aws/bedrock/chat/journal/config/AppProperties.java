package net.jrodolfo.aws.bedrock.chat.journal.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @Valid
    private Aws aws = new Aws();

    @Valid
    private Storage storage = new Storage();

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

    public static class Aws {

        @NotBlank
        private String region;

        @NotBlank
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

        @NotBlank
        private String sessionsDirectory;

        public String getSessionsDirectory() {
            return sessionsDirectory;
        }

        public void setSessionsDirectory(String sessionsDirectory) {
            this.sessionsDirectory = sessionsDirectory;
        }
    }
}
