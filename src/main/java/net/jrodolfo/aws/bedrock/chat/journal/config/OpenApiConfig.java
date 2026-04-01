package net.jrodolfo.aws.bedrock.chat.journal.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AWS Bedrock Chat Journal API")
                        .description("Spring Boot REST API for learning Amazon Bedrock Converse with file-backed chat sessions.")
                        .version("v1")
                        .contact(new Contact()
                                .name("Rod Oliveira")
                                .email("jrodolfo@gmail.com")
                                .url("https://jrodolfo.net")));
    }
}
