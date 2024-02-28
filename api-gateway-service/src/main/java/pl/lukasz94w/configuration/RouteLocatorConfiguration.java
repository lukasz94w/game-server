package pl.lukasz94w.configuration;

import org.springframework.cloud.gateway.filter.factory.DedupeResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import pl.lukasz94w.filter.AuthenticationFilter;

@Configuration
public class RouteLocatorConfiguration {

    // configuration can also be done in application.yaml
    @Bean
    public RouteLocator myRoutes(RouteLocatorBuilder builder, AuthenticationFilter authenticationFilter) {
        return builder.routes()
                .route(p -> p
                        .path("/websocket/**")
                        .filters(f -> f.dedupeResponseHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, DedupeResponseHeaderGatewayFilterFactory.Strategy.RETAIN_UNIQUE.name())
                                .dedupeResponseHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, DedupeResponseHeaderGatewayFilterFactory.Strategy.RETAIN_UNIQUE.name())
                                .filter(authenticationFilter.apply(new Object())))
                        .uri("lb:ws://game-server-core")
                )
                .route(p -> p
                        .path("/api/v1/history/findGamesForUser")
                        .filters(f -> f.filter(authenticationFilter.apply(new Object())))
                        .uri("lb://history-service")
                )
                .route(p -> p
                        .path("/api/v1/auth/**")
                        .uri("lb://auth-service")
                )
                .build();
    }
}
