package pl.lukasz94w.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<Object> {

    private final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

    private final WebClient.Builder webClientBuilder;

    public AuthenticationFilter(WebClient.Builder webClientBuilder) {
        super(Object.class);
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public GatewayFilter apply(Object o) {
        return ((exchange, chain) -> {
            HttpHeaders requestHeaders = exchange.getRequest().getHeaders();

            String cookie = extractAuthCookie(requestHeaders);
            if (cookie == null) {
                logger.info("Request rejected: missing auth cookie");
                return onError(exchange);
            }

            logger.info("Incoming request, cookie: {}", cookie);

            return webClientBuilder
                    .build()
                    .get()
                    .uri("lb://auth-service/api/v1/auth/verifySessionActive")
                    .headers(httpHeaders -> httpHeaders.addAll(requestHeaders))
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(response -> {
                        exchange.getRequest().mutate().header("userName", response);
                        return exchange;
                    })
                    .flatMap(response -> chain.filter(exchange))
                    .onErrorResume(error -> {
                        if (error instanceof WebClientResponseException webClientException) {
                            logger.error("WebClientResponseException occurred:, HTTP status code: {}, error message: {}", webClientException.getStatusCode(), webClientException.getStatusText());
                        } else {
                            logger.error("Exception different than WebClientResponseException occurred: {}", error.getMessage());
                        }
                        return onError(exchange);
                    });
        });
    }

    private String extractAuthCookie(HttpHeaders requestHeaders) {
        List<String> cookieHeaders = requestHeaders.get("cookie");
        if (cookieHeaders == null || cookieHeaders.isEmpty()) {
            return null;
        } else {
            String cookieHeader = cookieHeaders.getFirst();
            return cookieHeader.replace("SESSION=", "");
        }
    }

    private Mono<Void> onError(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }
}
