package com.example.mall.integration;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wires the AskFlow {@link RestClient}. We use {@code RestClient} (Spring 6.1) over WebClient to
 * avoid pulling in reactor-netty; outbox workers run on the scheduling thread pool and a blocking
 * client is the correct choice.
 */
@Configuration
@EnableConfigurationProperties(AskFlowProperties.class)
public class AskFlowConfig {

    @Bean
    public RestClient askFlowRestClient(AskFlowProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofMillis(props.getRequestTimeoutMs()).toMillis());
        factory.setReadTimeout((int) Duration.ofMillis(props.getRequestTimeoutMs()).toMillis());
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + props.getServiceToken())
                .build();
    }
}
