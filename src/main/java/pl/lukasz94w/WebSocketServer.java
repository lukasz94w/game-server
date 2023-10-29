package pl.lukasz94w;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalTime;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class WebSocketServer extends TextWebSocketHandler {
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("Server connection opened, session id: " + session.getId());
        //logger.info("Server connection opened");
        sessions.add(session);
        TextMessage message = new TextMessage("one-time message from server");
        //logger.info("Server sends: {}", message);
        session.sendMessage(message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        System.out.println("Connection closed, session id: " + session.getId());
        //logger.info("Server connection closed: {}", status);
        sessions.remove(session);
    }

    @Scheduled(fixedRate = 10000)
    void sendPeriodicMessages() throws IOException {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                String broadcast = "server periodic message " + LocalTime.now();
                //logger.info("Server sends: {}", broadcast);
                session.sendMessage(new TextMessage(broadcast));
            }
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String request = message.getPayload();
        System.out.println("Server received: " + request + " from user with session id: " + session.getId());
        //logger.info("Server received: {}", request);
//        String response = String.format("response from server to '%s'", HtmlUtils.htmlEscape(request));
        //logger.info("Server sends: {}", response);
        session.sendMessage(new TextMessage("Hello user: " + session.getId() + ", that's also your original message: " + request));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        System.out.println("HEEEELLLLOO");
        //logger.info("Server transport error: {}", exception.getMessage());
    }

//    @Override
//    public List<String> getSubProtocols() {
//        return Collections.singletonList("subprotocol.demo.websocket");
//    }
}
