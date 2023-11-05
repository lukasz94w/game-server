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

public class WebSocketServer extends TextWebSocketHandler {

    private final Logger logger = LoggerFactory.getLogger(WebSocketServer.class);

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    private final Map<WebSocketSession, Long> sessionsLastHeartbeat = new ConcurrentHashMap<>();

    private final List<Pair<WebSocketSession, WebSocketSession>> pairedSessions = new CopyOnWriteArrayList<>();

    private final static String SERVER_MESSAGE_KEY = "serverMessage";

    private final static String SERVER_HEARTBEAT = "serverHeartbeat";

    private final static String SERVER_PAIRED_SESSION_DISCONNECTED = "serverPairedSessionDisconnected";

    private final static String CLIENT_MESSAGE_KEY = "clientMessage";

    private final static String CLIENT_HEARTBEAT = "clientHeartBeat";

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("Server connection opened with session id: {}", session.getId());
        sessions.add(session);

        try {
            handleSessionsPairing(session);
        } catch (Exception e) {
            logger.error("Exception in afterConnectionEstablished: {}", e.getMessage());
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
            if (jsonMessage.has(CLIENT_MESSAGE_KEY)) {
                handleMessageForwarding(callingSession, jsonMessage);
            } else if (jsonMessage.has(CLIENT_HEARTBEAT)) {
                handleHeartbeat(callingSession);
            } else {
                logger.info("Unknown type of message from session: {}", callingSession.getId());
            }
        } catch (Exception e) {
            logger.error("Exception in handleTextMessage: {}", e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        // TODO: I think this method could be used when f.e. session cannot send a message to it's paired counterpart, then we could try to implement
        // retry policy of sending messages or something like that
        logger.error("Exception in handleTransportError: {}", exception.getMessage());
    }

    private void handleSessionsPairing(WebSocketSession newSession) throws IOException {
        if (sessions.size() % 2 != 0) {
            pairedSessions.add(new Pair<>(newSession, null));

            JSONObject jsonMessage = new JSONObject().put(SERVER_MESSAGE_KEY, "Successfully connected. Waiting for another player to join...");
            newSession.sendMessage(new TextMessage(jsonMessage.toString()));
        } else {
            WebSocketSession lonelySession = pairedSessions.getLast().getValue0();
            pairedSessions.removeLast();
            pairedSessions.add(new Pair<>(lonelySession, newSession));

            JSONObject jsonMessageForLastSessionWithoutPair = new JSONObject().put("serverMessage", "Your opponent has connected. Let's the party started!");
            lonelySession.sendMessage(new TextMessage(jsonMessageForLastSessionWithoutPair.toString()));

            JSONObject jsonMessageForNewSession = new JSONObject().put(SERVER_MESSAGE_KEY, "Successfully connected. Good luck!");
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
            JSONObject jsonMessage = new JSONObject().put(SERVER_MESSAGE_KEY, messageFromMessagingSession.getString("clientMessage"));
            pairedSession.sendMessage(new TextMessage(jsonMessage.toString()));
        } else {
            JSONObject jsonMessage = new JSONObject().put(SERVER_MESSAGE_KEY, "Please wait for the next player to join...");
            messagingSession.sendMessage(new TextMessage(jsonMessage.toString()));
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

            sessions.remove(pairedSession);
            sessions.remove(disconnectingSession);
            removePairFromPairs(disconnectingSession);
        } else {
            sessions.remove(disconnectingSession);
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

    @Scheduled(fixedRate = 10000)
    void inactiveSessionsCleaner() {
        long currentTimestamp = System.currentTimeMillis();
        for (WebSocketSession session : sessionsLastHeartbeat.keySet()) {
            long lastHeartbeat = sessionsLastHeartbeat.get(session);
            if (currentTimestamp - lastHeartbeat > 35000 && session.isOpen()) {
                try {
                    logger.info("Inactive session detected: {}, closing it...", session.getId());
                    session.close(); // I saw it probably cause afterConnectionClose to be triggered, so maybe there removed paired sessions and inform paired session about closing it?
                    sessions.remove(session);
                    sessionsLastHeartbeat.remove(session);
                } catch (IOException e) {
                    logger.error("Exception in inactiveSessionsCleaner, during closing idle session with id: {}", session.getId());
                }
            }
        }
    }
}
