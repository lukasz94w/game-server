package pl.lukasz94w.logging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RequestLoggingFilterConfig {

    @Bean
    public WithoutLoggingBeforeRequestFilter logFilter() {
        WithoutLoggingBeforeRequestFilter filter = new WithoutLoggingBeforeRequestFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludeHeaders(true);
        filter.setAfterMessagePrefix("Request headers: ");
        return filter;
    }
}
