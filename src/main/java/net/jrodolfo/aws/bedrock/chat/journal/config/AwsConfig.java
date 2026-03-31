package net.jrodolfo.aws.bedrock.chat.journal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
public class AwsConfig {

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient(AppProperties appProperties) {
        return BedrockRuntimeClient.builder()
                .region(Region.of(appProperties.getAws().getRegion()))
                .build();
    }
}
