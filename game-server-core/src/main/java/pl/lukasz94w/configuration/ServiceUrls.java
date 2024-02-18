package pl.lukasz94w.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceUrls {

    @Value("${pl.lukasz94w.verifySessionActiveUrl}")
    public String verifySessionActiveUrl;

    @Value("${pl.lukasz94w.getUserNameUrl}")
    public String getUserNameUrl;

    @Value("${pl.lukasz94w.saveGameUrl}")
    public String saveGameUrl;
}
