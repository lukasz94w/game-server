package pl.lukasz94w;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

public class CustomHandshakeInterceptor implements HandshakeInterceptor {

    // TODO: add logger here which logs the unsuccessful (and successful?) trials of connection so unauthenticated and authenticated user

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // TODO: here can be a call for auth service endpoint which return "ok" message or sth like that or just
        // treat that if we get an 200 HTTP response during access of the protected endpoint we are authenticated then.
        // If i want to use session cookie then I will be sending it and checking whether I have an access to protected endpoints?
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {

    }
}
