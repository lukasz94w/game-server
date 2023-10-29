package pl.lukasz94w;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class WebSocketServer extends TextWebSocketHandler {

    private final Logger logger = LoggerFactory.getLogger(WebSocketServer.class);
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    private int numberOfOpenedSessions = 0;
    private final LinkedHashMap<WebSocketSession, WebSocketSession> pairedSessions = new LinkedHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("Server connection opened with session id: {}", session.getId());
        handleSessionsNumbers(session);
        handleSessionsPairing(session);
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
    public void handleTextMessage(WebSocketSession messagingSession, TextMessage message) throws Exception {
        WebSocketSession pairedSession = findPairedSession(messagingSession);

        if (pairedSession != null) {
            JSONObject o = new JSONObject(message.getPayload());
            String extractedMessage = o.getString("message");
            pairedSession.sendMessage(new TextMessage(extractedMessage));
        } else {
            messagingSession.sendMessage(new TextMessage("Please wait for the next player to join..."));
        }
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

    private void handleSessionsNumbers(WebSocketSession session) {
        sessions.add(session);
        numberOfOpenedSessions++;
    }

    private void handleSessionsPairing(WebSocketSession newSession) throws IOException {
        if (numberOfOpenedSessions % 2 != 0) {
            pairedSessions.put(newSession, null);
            newSession.sendMessage(new TextMessage("Successfully connected. Waiting for another player to join..."));
        } else {
            WebSocketSession lastSessionWithoutAssignedPair = pairedSessions.lastEntry().getKey();
            pairedSessions.put(lastSessionWithoutAssignedPair, newSession);
            lastSessionWithoutAssignedPair.sendMessage(new TextMessage("Your opponent has connected. Let's the party started!"));
            newSession.sendMessage(new TextMessage("Successfully connected. Good luck!"));
        }

        logger.info("Summarize of current sessions: {}", pairedSessions);
    }

    private WebSocketSession findPairedSession(WebSocketSession messagingSession) {
        WebSocketSession pairedSession;

        // find by key
        pairedSession = pairedSessions.get(messagingSession);
        if (pairedSession != null) {
            return pairedSession;
        }

        // find by value
        Set<Map.Entry<WebSocketSession, WebSocketSession>> sessionEntries = pairedSessions.entrySet();
        for (Map.Entry<WebSocketSession, WebSocketSession> sessionEntry : sessionEntries) {
            if (sessionEntry.getValue().equals(messagingSession))
                pairedSession = sessionEntry.getKey();
        }

        return pairedSession;
    }
}
