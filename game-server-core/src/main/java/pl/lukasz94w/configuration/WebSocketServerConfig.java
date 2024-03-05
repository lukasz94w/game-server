package pl.lukasz94w.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "pl.lukasz94w")
@Getter
@Setter
public class WebSocketServerConfig {

    public Integer maxNumberOfGames;

    public Integer requiredHeartbeatFrequency;
}
