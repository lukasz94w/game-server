package pl.lukasz94w.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<Object> {

    private final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

    private final WebClient.Builder webClientBuilder;

    private final Pattern patternForGettingCookieSessionValue = Pattern.compile("SESSION=([^;]+)");

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

            // is it ok to build webClient each time it's needed?
            return webClientBuilder
                    .build()
                    .get()
                    .uri("lb://auth-service/api/v1/auth/verifyCookieAndGetUserName")
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
                            logger.error("Exception occurred: {}", error.getMessage());
                        }
                        return onError(exchange);
                    });
        });
    }

    private String extractAuthCookie(HttpHeaders requestHeaders) {
        List<String> cookieHeaders = requestHeaders.get("Cookie");

        if (cookieHeaders == null || cookieHeaders.isEmpty()) {
            return null;
        } else {
            String cookiesWithValues = cookieHeaders.getFirst();
            Matcher matcher = patternForGettingCookieSessionValue.matcher(cookiesWithValues);
            if (matcher.find()) {
                return matcher.group(1);
            } else {
                return null;
            }
        }
    }

    private Mono<Void> onError(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        return response.setComplete();
    }
}
