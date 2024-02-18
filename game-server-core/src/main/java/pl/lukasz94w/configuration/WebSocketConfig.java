package pl.lukasz94w.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import pl.lukasz94w.GameServer;
import pl.lukasz94w.commons.GameServerUtil;
import pl.lukasz94w.interceptor.AuthenticationHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketServerConfig webSocketServerConfig;

    private final ServiceUrls serviceUrls;

    private final GameServerUtil gameServerUtil;

    public WebSocketConfig(WebSocketServerConfig webSocketServerConfig, ServiceUrls serviceUrls, GameServerUtil gameServerUtil) {
        this.webSocketServerConfig = webSocketServerConfig;
        this.serviceUrls = serviceUrls;
        this.gameServerUtil = gameServerUtil;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler(), "/websocket")
                .setAllowedOrigins("http://localhost:3000")
                .withSockJS()
                //.setInterceptors(handshakeInterceptor())
                .setWebSocketEnabled(true)
                .setHeartbeatTime(25000)
                .setDisconnectDelay(5000)
                .setClientLibraryUrl("/webjars/sockjs-client/1.1.2/sockjs.js")
                .setSessionCookieNeeded(false);
    }

    @Bean
    public WebSocketHandler webSocketHandler() {
        return new GameServer(webSocketServerConfig, serviceUrls, gameServerUtil);
    }

    @Bean
    public HandshakeInterceptor handshakeInterceptor() {
        return new AuthenticationHandshakeInterceptor();
    }
}
