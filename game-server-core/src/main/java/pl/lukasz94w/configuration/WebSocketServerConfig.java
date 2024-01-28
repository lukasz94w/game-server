package pl.lukasz94w.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebSocketServerConfig {

    @Value("${pl.lukasz94w.maxNumberOfGames}")
    public Integer maxNumberOfGames;

    @Value("${pl.lukasz94w.requiredHeartbeatFrequency}")
    public Integer requiredHeartbeatFrequency;
}
