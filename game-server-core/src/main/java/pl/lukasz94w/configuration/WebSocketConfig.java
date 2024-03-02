package pl.lukasz94w.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import pl.lukasz94w.GameServer;
import pl.lukasz94w.interceptor.LoggingHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LoggingHandshakeInterceptor loggingHandshakeInterceptor;

    private final WebSocketServerConfig webSocketServerConfig;

    private final RestTemplate historyServiceClient;

    public WebSocketConfig(LoggingHandshakeInterceptor loggingHandshakeInterceptor, WebSocketServerConfig webSocketServerConfig, RestTemplate historyServiceClient) {
        this.loggingHandshakeInterceptor = loggingHandshakeInterceptor;
        this.webSocketServerConfig = webSocketServerConfig;
        this.historyServiceClient = historyServiceClient;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler(), "/websocket")
                .setAllowedOrigins("http://localhost:3000")
                .withSockJS()
                .setInterceptors(loggingHandshakeInterceptor)
                .setWebSocketEnabled(true)
                .setHeartbeatTime(25000)
                .setDisconnectDelay(5000)
                .setClientLibraryUrl("/webjars/sockjs-client/1.1.2/sockjs.js");
    }

    @Bean
    public WebSocketHandler webSocketHandler() {
        return new GameServer(webSocketServerConfig, historyServiceClient);
    }
}