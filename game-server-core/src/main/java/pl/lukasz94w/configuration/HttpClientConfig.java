package pl.lukasz94w.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

@Configuration
public class HttpClientConfig {

    @Value("${pl.lukasz94w.historyServiceSaveGameUrl}")
    public String saveGameUrl;

    @LoadBalanced
    @Bean
    public RestTemplate historyServiceClient() {
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory(saveGameUrl);
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setUriTemplateHandler(uriBuilderFactory);
        return restTemplate;
    }
}
