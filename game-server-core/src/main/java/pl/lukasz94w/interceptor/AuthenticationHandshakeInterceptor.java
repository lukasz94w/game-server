package pl.lukasz94w.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import pl.lukasz94w.WebSocketServer;

import java.util.List;
import java.util.Map;

// Originally here was interception of incoming websocket request and validation whether session cookie is valid.
// It turned out when it's implemented here there is problem with proper receiving of close socket. For some reason
// it couldn't reach the server. Therefore, I moved the validation to server itself (cookie is checked just after
// connection is established. See more at: WebSocketServer class, afterConnectionEstablished() method.
public class AuthenticationHandshakeInterceptor implements HandshakeInterceptor {

    private final Logger logger = LoggerFactory.getLogger(WebSocketServer.class);

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // catch original request containing session cookie and send it to validation
        // endpoint to check whether the session exist for sent cookie
        HttpHeaders headers = request.getHeaders();
        HttpEntity<String> httpEntity = new HttpEntity<>(removeWebSocketHeaders(headers));

        ResponseEntity<String> responseEntity = restTemplate().exchange("http://localhost:8093/api/v1/auth/verifySignedIn", HttpMethod.GET, httpEntity, String.class);

        HttpStatusCode responseStatusCode = responseEntity.getStatusCode();
        String responseBody = responseEntity.getBody();

        boolean acceptConnection;

        String cookie = request.getHeaders().getFirst("cookie");
        if (responseStatusCode.equals(HttpStatus.OK)) {
            logger.info("Request successfully validated for cookie: {}", cookie);
            System.out.println(responseBody);
            acceptConnection = true;
        } else {
            logger.info("Unsuccessful attempt of connection, cookie: {}", cookie);
            acceptConnection = false;
        }

        return acceptConnection;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {

    }

    // Method which removes WebSocket typical headers. Because request is send to http endpoint (not WebSocket endpoint) it's good
    // practice to remove original WebSocket headers which were received from the WebSocket client. Note: I tested and the 200 HTTP
    // status is sent back even if these headers are not used (of course session cookie must be valid).
    private HttpHeaders removeWebSocketHeaders(HttpHeaders originalHeaders) {
        List<String> webSocketHeaders = List.of("connection", "upgrade", "sec-websocket-version", "sec-websocket-key", "sec-websocket-extensions");
        webSocketHeaders.forEach(originalHeaders::remove);
        return originalHeaders;
    }

    @Bean
    private RestTemplate restTemplate() {
        return new RestTemplate();
    }
}