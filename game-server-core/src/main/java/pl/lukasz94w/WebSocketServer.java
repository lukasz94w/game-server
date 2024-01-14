package pl.lukasz94w;

import org.javatuples.Pair;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
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

import static pl.lukasz94w.JsonMessageKey.*;

public class WebSocketServer extends TextWebSocketHandler {

    private final Logger logger = LoggerFactory.getLogger(WebSocketServer.class);

    private final Set<WebSocketSession> activeSessions = new CopyOnWriteArraySet<>();

    private final Map<WebSocketSession, Long> activeSessionsLastHeartbeat = new ConcurrentHashMap<>();

    private final List<Pair<WebSocketSession, WebSocketSession>> activePairedSessions = new CopyOnWriteArrayList<>();

    @Value("${pl.lukasz94w.maximumNumberOfActiveSessions}")
    private Integer maximumNumberOfActiveSessions;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        if (activeSessions.size() >= maximumNumberOfActiveSessions) {
            rejectSession(session, "Maximum number of active sessions exceeded. Try again later");
        } else if (!checkWhetherUserAuthenticated(session.getHandshakeHeaders())) {
            rejectSession(session, "User unauthenticated");
        } else {
            acceptSession(session);
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
        for (WebSocketSession session : activeSessionsLastHeartbeat.keySet()) {
            Long lastHeartbeat = activeSessionsLastHeartbeat.get(session);
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

    private boolean checkWhetherUserAuthenticated(HttpHeaders handshakeHeaders) {
        // catch original request containing session cookie and send it to validation
        // endpoint to check whether the session exist for sent cookie
        HttpEntity<String> httpEntity = new HttpEntity<>(removeWebSocketHeaders(handshakeHeaders));

        ResponseEntity<String> responseEntity;
        try {
            responseEntity = new RestTemplate().exchange("http://localhost:8093/api/v1/auth/verifySignedIn", HttpMethod.GET, httpEntity, String.class);
        } catch (HttpClientErrorException e) {
            logger.info("Unauthorized attempt of connection"); // ip address could be added here (passed from handshake interceptor f.e.)
            return false;
        }

        HttpStatusCode responseStatusCode = responseEntity.getStatusCode();
        assert (responseStatusCode.equals(HttpStatus.OK));
        String cookie = handshakeHeaders.getFirst("cookie");
        logger.info("Request successfully validated for cookie: {}. Response status: {}", cookie, responseStatusCode);
        return true;
    }

    // Method which removes WebSocket typical headers. Because request is send to http endpoint (not WebSocket endpoint) it's good
    // practice to remove original WebSocket headers which were received from the WebSocket client. Note: I tested and the 200 HTTP
    // status is sent back even if these headers are not used (of course session cookie must be valid).
    private HttpHeaders removeWebSocketHeaders(HttpHeaders originalHeaders) {
        List<String> webSocketHeaders = List.of("connection", "upgrade", "sec-websocket-version", "sec-websocket-key", "sec-websocket-extensions");

        HttpHeaders writableHttpHeaders = HttpHeaders.writableHttpHeaders(originalHeaders);
        webSocketHeaders.forEach(writableHttpHeaders::remove);

        return originalHeaders;
    }

    private void acceptSession(WebSocketSession session) {
        logger.info("Server connection opened with session id: {}", session.getId());
        addSession(session);

        try {
            handleSessionsPairing(session);
        } catch (Exception e) {
            logger.error("Exception in acceptSession: {}", e.getMessage());
            removeSession(session);
        }
    }

    private void rejectSession(WebSocketSession session, String rejectionReason) {
        try {
            session.sendMessage(jsonMessage(SERVER_REJECTION_MESSAGE, rejectionReason));

            new Thread(() -> {
                try {
                    Thread.sleep(10000);

                    // cleaning just in case rejected session haven't closed the session after 10 seconds since receiving rejection message
                    if (session.isOpen()) {
                        session.close();
                    }
                } catch (Exception e) {
                    logger.error("Exception during closing the rejected session: {}", e.getMessage());
                }

            }).start();
        } catch (Exception e) {
            logger.error("Exception in rejectSession: {}", e.getMessage());
        }
    }

    private void handleSessionsPairing(WebSocketSession newSession) throws IOException {
        if (activeSessions.size() % 2 != 0) {
            activePairedSessions.add(new Pair<>(newSession, null));
            newSession.sendMessage(jsonMessage(SERVER_MESSAGE, "Successfully connected. Waiting for another player to join..."));
        } else {
            WebSocketSession lonelySession = activePairedSessions.getLast().getValue0();
            activePairedSessions.removeLast();
            activePairedSessions.add(new Pair<>(lonelySession, newSession));

            lonelySession.sendMessage(jsonMessage(SERVER_MESSAGE, "Your opponent has connected. Let's the party started!"));
            newSession.sendMessage(jsonMessage(SERVER_MESSAGE, "Successfully connected. Good luck!"));
        }

        logger.info("Summarize of current sessions: {}", activePairedSessions);
    }

    private void handleHeartbeat(WebSocketSession callingSession) throws IOException {
        activeSessionsLastHeartbeat.put(callingSession, System.currentTimeMillis());
        callingSession.sendMessage(jsonMessage(SERVER_HEARTBEAT, String.valueOf(System.currentTimeMillis())));
    }

    private void handleMessageForwarding(WebSocketSession messagingSession, JSONObject messageFromMessagingSession) throws IOException {
        WebSocketSession pairedSession = findPairedSession(messagingSession);
        if (pairedSession != null) {
            pairedSession.sendMessage(jsonMessage(SERVER_MESSAGE, messageFromMessagingSession.getString(CLIENT_MESSAGE)));
        } else {
            messagingSession.sendMessage(jsonMessage(SERVER_MESSAGE, "Please wait for the next player to join..."));
        }
    }

    private void handleConfirmationForwarding(WebSocketSession messagingSession) throws IOException {
        WebSocketSession pairedSession = findPairedSession(messagingSession);
        if (pairedSession != null) {
            pairedSession.sendMessage(jsonMessage(SERVER_CLIENT_RECEIVED_MESSAGE_CONFIRMATION, "Ok"));
        }
    }

    private void handleDisconnection(WebSocketSession disconnectingSession) throws IOException {
        WebSocketSession pairedSession = findPairedSession(disconnectingSession);

        if (pairedSession != null) {
            if (pairedSession.isOpen()) {
                pairedSession.sendMessage(jsonMessage(SERVER_PAIRED_SESSION_DISCONNECTED, "Your opponent has disconnected"));
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
        for (Pair<WebSocketSession, WebSocketSession> pairedSession : activePairedSessions) {
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
        for (Pair<WebSocketSession, WebSocketSession> pairedSession : activePairedSessions) {
            if (pairedSession.getValue0() == disconnectingSession || pairedSession.getValue1() == disconnectingSession) {
                activePairedSessions.remove(pairedSession);
                break;
            }
        }
    }

    private void removeLonelyFromPairs(WebSocketSession disconnectingSession) {
        for (Pair<WebSocketSession, WebSocketSession> lonelySession : activePairedSessions) {
            if (lonelySession.getValue0() == disconnectingSession) {
                activePairedSessions.remove(lonelySession);
                break;
            }
        }
    }

    private void addSession(WebSocketSession newSession) {
        activeSessions.add(newSession);
        activeSessionsLastHeartbeat.put(newSession, System.currentTimeMillis());
    }

    private void removeSession(WebSocketSession disconnectingSession) {
        activeSessions.remove(disconnectingSession);
        activeSessionsLastHeartbeat.remove(disconnectingSession);
    }

    private TextMessage jsonMessage(String messageKey, String messageValue) {
        JSONObject jsonMessage = new JSONObject().put(messageKey, messageValue);
        return new TextMessage(jsonMessage.toString());
    }
}
