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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class WebSocketServer extends TextWebSocketHandler {

    private final Logger logger = LoggerFactory.getLogger(WebSocketServer.class);
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    private int numberOfOpenedSessions = 0;
    private final LinkedHashMap<WebSocketSession, WebSocketSession> pairedSessions = new LinkedHashMap<>();
    private final Map<WebSocketSession, Long> lastHeartbeats = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("Server connection opened with session id: {}", session.getId());
        initializeSession(session);
        handleSessionsPairing(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // interesting it got triggered when client lost the connection (the status.reason was Go away!)
        // TODO: inform there paired session about losing connectivity by sending specific message f.e. "session ended"
        // also remove there paired sessions
        logger.info("Server connection closed: {}", status);
        sessions.remove(session);
    }

    @Override
    public void handleTextMessage(WebSocketSession callingSession, TextMessage message) throws Exception {
        String extractedMessage = new JSONObject(message.getPayload()).getString("message");

        if (extractedMessage.equals("heartbeat_iss")) {
            handleHeartbeat(callingSession);
        } else {
            handleMessageForwarding(callingSession, extractedMessage);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        // TODO: I think this method could be used when f.e. session cannot send a message to it's paired counterpart, then we could try to implement
        // retry policy of sending messages or something like that
        logger.info("Server transport error: {}", exception.getMessage());
    }

    @Scheduled(fixedRate = 10000)
    void inactiveSessionsCleaner() {
        long currentTimestamp = System.currentTimeMillis();
        for (WebSocketSession session : lastHeartbeats.keySet()) {
            long lastHeartbeat = lastHeartbeats.get(session);
            if (currentTimestamp - lastHeartbeat > 35000 && session.isOpen()) {
                try {
                    logger.info("Inactive session detected: {}, closing it...", session.getId());
                    session.close(); // I saw it probably cause afterConnectionClose to be triggered, so maybe there removed paired sessions and inform paired session about closing it?
                    sessions.remove(session);
                    lastHeartbeats.remove(session);
                } catch (IOException e) {
                    logger.error("Exception during closing idle session with id: {}", session.getId());
                }
            }
        }
    }

    private void initializeSession(WebSocketSession session) {
        sessions.add(session);
        numberOfOpenedSessions++;
        lastHeartbeats.put(session, System.currentTimeMillis());
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

    private void handleHeartbeat(WebSocketSession callingSession) throws IOException {
        lastHeartbeats.put(callingSession, System.currentTimeMillis());
        callingSession.sendMessage(new TextMessage("heartbeat_ack"));
    }

    private void handleMessageForwarding(WebSocketSession callingSession, String extractedMessage) throws IOException {
        WebSocketSession pairedSession = findPairedSession(callingSession);

        if (pairedSession != null) {
            pairedSession.sendMessage(new TextMessage(extractedMessage));
        } else {
            callingSession.sendMessage(new TextMessage("Please wait for the next player to join..."));
        }
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
            if (sessionEntry.getValue() != null) {
                if (sessionEntry.getValue().equals(messagingSession)) {
                    pairedSession = sessionEntry.getKey();
                }
            }
        }

        return pairedSession;
    }
}
