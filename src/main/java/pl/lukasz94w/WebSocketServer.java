package pl.lukasz94w;

import org.javatuples.Pair;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import static pl.lukasz94w.JsonKey.*;

public class WebSocketServer extends TextWebSocketHandler {

    private final Logger logger = LoggerFactory.getLogger(WebSocketServer.class);

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    private final Map<WebSocketSession, Long> sessionsLastHeartbeat = new ConcurrentHashMap<>();

    private final List<Pair<WebSocketSession, WebSocketSession>> pairedSessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("Server connection opened with session id: {}", session.getId());
        addSession(session);

        try {
            handleSessionsPairing(session);
        } catch (Exception e) {
            logger.error("Exception in afterConnectionEstablished: {}", e.getMessage());
            removeSession(session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession disconnectingSession, CloseStatus status) {
        logger.info("Server connection closed: {}, session id: {}", status, disconnectingSession.getId());

        try {
            handleDisconnection(disconnectingSession);
        } catch (Exception e) {
            logger.error("Exception in afterConnectionClosed: {}", e.getMessage());
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession callingSession, TextMessage message) {
        JSONObject jsonMessage = new JSONObject(message.getPayload());

        try {
            if (jsonMessage.has(CLIENT_MESSAGE)) {
                handleMessageForwarding(callingSession, jsonMessage);
            } else if (jsonMessage.has(CLIENT_HEARTBEAT)) {
                handleHeartbeat(callingSession);
            } else if (jsonMessage.has(CLIENT_RECEIVED_MESSAGE_CONFIRMATION)) {
                handleConfirmationForwarding(callingSession);
            } else {
                logger.error("Unknown type of message from session: {}", callingSession.getId());
            }
        } catch (Exception e) {
            logger.error("Exception in handleTextMessage: {}", e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.info("HandleTransportError triggered, session: {}", session.getId()); // for logging purposes to check when it is triggered, for the time being it's not clear for me
        logger.error("Exception in handleTransportError: {}", exception.getMessage());
    }

    @Scheduled(fixedRate = 30000)
    void inactiveSessionsCleaner() {
        long currentTimestamp = System.currentTimeMillis();
        int requiredHeartbeatFrequencyInSeconds = 65;
        for (WebSocketSession session : sessionsLastHeartbeat.keySet()) {
            Long lastHeartbeat = sessionsLastHeartbeat.get(session);
            if (currentTimestamp - lastHeartbeat > requiredHeartbeatFrequencyInSeconds * 1000 && session.isOpen()) {
                try {
                    logger.info("Inactive session detected: {}, closing it...", session.getId());
                    session.close(); // triggers afterConnectionClose() and in consequence handleDisconnection()
                } catch (IOException e) {
                    logger.error("Exception in inactiveSessionsCleaner, during closing idle session with id: {}", session.getId());
                }
            }
        }
    }

    private void handleSessionsPairing(WebSocketSession newSession) throws IOException {
        if (sessions.size() % 2 != 0) {
            pairedSessions.add(new Pair<>(newSession, null));

            JSONObject jsonMessage = new JSONObject().put(SERVER_MESSAGE, "Successfully connected. Waiting for another player to join...");
            newSession.sendMessage(new TextMessage(jsonMessage.toString()));
        } else {
            WebSocketSession lonelySession = pairedSessions.getLast().getValue0();
            pairedSessions.removeLast();
            pairedSessions.add(new Pair<>(lonelySession, newSession));

            JSONObject jsonMessageForLastSessionWithoutPair = new JSONObject().put("serverMessage", "Your opponent has connected. Let's the party started!");
            lonelySession.sendMessage(new TextMessage(jsonMessageForLastSessionWithoutPair.toString()));

            JSONObject jsonMessageForNewSession = new JSONObject().put(SERVER_MESSAGE, "Successfully connected. Good luck!");
            newSession.sendMessage(new TextMessage(jsonMessageForNewSession.toString()));
        }

        logger.info("Summarize of current sessions: {}", pairedSessions);
    }

    private void handleHeartbeat(WebSocketSession callingSession) throws IOException {
        sessionsLastHeartbeat.put(callingSession, System.currentTimeMillis());
        JSONObject jsonMessage = new JSONObject().put(SERVER_HEARTBEAT, System.currentTimeMillis());
        callingSession.sendMessage(new TextMessage(jsonMessage.toString()));
    }

    private void handleMessageForwarding(WebSocketSession messagingSession, JSONObject messageFromMessagingSession) throws IOException {
        WebSocketSession pairedSession = findPairedSession(messagingSession);
        if (pairedSession != null) {
            JSONObject jsonMessage = new JSONObject().put(SERVER_MESSAGE, messageFromMessagingSession.getString("clientMessage"));
            pairedSession.sendMessage(new TextMessage(jsonMessage.toString()));
        } else {
            JSONObject jsonMessage = new JSONObject().put(SERVER_MESSAGE, "Please wait for the next player to join...");
            messagingSession.sendMessage(new TextMessage(jsonMessage.toString()));
        }
    }

    private void handleConfirmationForwarding(WebSocketSession messagingSession) throws IOException {
        WebSocketSession pairedSession = findPairedSession(messagingSession);
        if (pairedSession != null) {
            JSONObject jsonMessage = new JSONObject().put(SERVER_CLIENT_RECEIVED_MESSAGE_CONFIRMATION, "Ok");
            pairedSession.sendMessage(new TextMessage(jsonMessage.toString()));
        }
    }

    private void handleDisconnection(WebSocketSession disconnectingSession) throws IOException {
        WebSocketSession pairedSession = findPairedSession(disconnectingSession);

        if (pairedSession != null) {
            if (pairedSession.isOpen()) {
                JSONObject jsonMessage = new JSONObject().put(SERVER_PAIRED_SESSION_DISCONNECTED, "Your opponent has disconnected");
                pairedSession.sendMessage(new TextMessage(jsonMessage.toString()));
                pairedSession.close();
            }

            removeSession(pairedSession);
            removeSession(disconnectingSession);
            removePairFromPairs(disconnectingSession);
        } else {
            removeSession(disconnectingSession);
            removeLonelyFromPairs(disconnectingSession);
        }
    }

    private WebSocketSession findPairedSession(WebSocketSession messagingSession) {
        for (Pair<WebSocketSession, WebSocketSession> pairedSession : pairedSessions) {
            if (pairedSession.getValue0() == messagingSession) {
                return pairedSession.getValue1();
            }
            if (pairedSession.getValue1() == messagingSession) {
                return pairedSession.getValue0();
            }
        }

        return null;
    }

    private void removePairFromPairs(WebSocketSession disconnectingSession) {
        for (Pair<WebSocketSession, WebSocketSession> pairedSession : pairedSessions) {
            if (pairedSession.getValue0() == disconnectingSession || pairedSession.getValue1() == disconnectingSession) {
                pairedSessions.remove(pairedSession);
                break;
            }
        }
    }

    private void removeLonelyFromPairs(WebSocketSession disconnectingSession) {
        for (Pair<WebSocketSession, WebSocketSession> lonelySession : pairedSessions) {
            if (lonelySession.getValue0() == disconnectingSession) {
                pairedSessions.remove(lonelySession);
                break;
            }
        }
    }

    private void addSession(WebSocketSession newSession) {
        sessions.add(newSession);
        sessionsLastHeartbeat.put(newSession, System.currentTimeMillis());
    }

    private void removeSession(WebSocketSession disconnectingSession) {
        sessions.remove(disconnectingSession);
        sessionsLastHeartbeat.remove(disconnectingSession);
    }
}
