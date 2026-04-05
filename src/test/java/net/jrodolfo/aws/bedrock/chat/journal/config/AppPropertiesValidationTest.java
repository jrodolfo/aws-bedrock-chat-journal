package net.jrodolfo.aws.bedrock.chat.journal.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "app.aws.region=us-east-1",
                    "app.aws.default-model-id=amazon.nova-lite-v1:0",
                    "app.storage.sessions-directory=data/sessions",
                    "app.limits.max-messages-per-session=100",
                    "app.inference.temperature=0.7",
                    "app.inference.top-p=0.9",
                    "app.inference.max-tokens=512",
                    "app.logging.log-bedrock-payloads=false");

    @Test
    void failsWhenAwsRegionIsBlank() {
        contextRunner
                .withPropertyValues("app.aws.region= ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseOf(context.getStartupFailure()).getMessage())
                            .contains("aws.region")
                            .contains("must not be blank");
                });
    }

    @Test
    void failsWhenDefaultModelIdIsBlank() {
        contextRunner
                .withPropertyValues("app.aws.default-model-id= ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseOf(context.getStartupFailure()).getMessage())
                            .contains("aws.defaultModelId")
                            .contains("must not be blank");
                });
    }

    @Test
    void failsWhenSessionsDirectoryIsBlank() {
        contextRunner
                .withPropertyValues("app.storage.sessions-directory= ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseOf(context.getStartupFailure()).getMessage())
                            .contains("storage.sessionsDirectory")
                            .contains("must not be blank");
                });
    }

    @Test
    void bindsSuccessfullyWithRequiredPropertiesPresent() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AppProperties.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    static class TestConfiguration {
    }

    private static Throwable rootCauseOf(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
