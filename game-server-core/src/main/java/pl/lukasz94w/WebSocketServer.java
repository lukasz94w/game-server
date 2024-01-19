package pl.lukasz94w;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.javatuples.Triplet;
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
import pl.lukasz94w.game.Game;
import pl.lukasz94w.game.GameException;
import pl.lukasz94w.game.GameFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static pl.lukasz94w.message.Client.*;
import static pl.lukasz94w.message.Server.*;

public class WebSocketServer extends TextWebSocketHandler {

    private final Logger logger = LoggerFactory.getLogger(WebSocketServer.class);

    private final Map<WebSocketSession, Long> sessionsLastHeartbeat = new ConcurrentHashMap<>();

    private final List<Triplet<WebSocketSession, WebSocketSession, Game>> activeGames = new CopyOnWriteArrayList<>();

    @Value("${pl.lukasz94w.maximumNumberOfGames}")
    private Integer maximumNumberOfGames;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        if (activeGames.size() >= maximumNumberOfGames) {
            rejectSession(session, "Maximum number of active sessions exceeded. Try again later");
        } else if (!checkAuthentication(session.getHandshakeHeaders())) {
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
            logger.error("Exception in afterConnectionClosed: {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        JSONObject jsonMessage = new JSONObject(message.getPayload());

        try {
            if (jsonMessage.has(CLIENT_MESSAGE_NEW_MESSAGE)) {
                handleMessageForwarding(session, jsonMessage);
            } else if (jsonMessage.has(CLIENT_GAME_UPDATE_CHOSEN_SQUARE_NUMBER)) {
                handleGameUpdate(session, jsonMessage);
            } else if (jsonMessage.has(CLIENT_SESSION_STATUS_UPDATE_HEARTBEAT)) {
                handleHeartbeat(session);
            } else if (jsonMessage.has(CLIENT_MESSAGE_RECEIVED_MESSAGE_CONFIRMATION)) {
                handleMessageConfirmationForwarding(session);
            } else if (jsonMessage.has(CLIENT_MESSAGE_RECEIVED_GAME_STATUS_UPDATE_CONFIRMATION)) {
                handleGameStatusUpdateConfirmationForwarding(session);
            } else {
                logger.error("Unknown type of message from session: {}", session.getId());
            }
        } catch (Exception e) {
            logger.error("Exception in handleTextMessage: {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.info("HandleTransportError triggered, session: {}", session.getId()); // for logging purposes to check when it is triggered, for the time being it's not clear for me
        logger.error("Exception in handleTransportError: {}", ExceptionUtils.getStackTrace(exception));
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

    private boolean checkAuthentication(HttpHeaders handshakeHeaders) {
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

        return writableHttpHeaders;
    }

    private void acceptSession(WebSocketSession session) {
        logger.info("Server connection opened with session id: {}", session.getId());
        addSession(session);

        try {
            handleSessionsPairing(session);
        } catch (Exception e) {
            logger.error("Exception in acceptSession: {}", ExceptionUtils.getStackTrace(e));
            removeSession(session);
        }
    }

    private void rejectSession(WebSocketSession session, String rejectionReason) {
        try {
            session.sendMessage(jsonMessage(SERVER_SESSION_STATUS_UPDATE_SESSION_REJECTED, rejectionReason));

            new Thread(() -> {
                try {
                    Thread.sleep(10000);

                    // cleaning just in case rejected session haven't closed the session after 10 seconds since receiving rejection message
                    if (session.isOpen()) {
                        session.close();
                    }
                } catch (Exception e) {
                    logger.error("Exception during closing the rejected session: {}", ExceptionUtils.getStackTrace(e));
                }

            }).start();
        } catch (Exception e) {
            logger.error("Exception in rejectSession: {}", ExceptionUtils.getStackTrace(e));
        }
    }

    private void handleSessionsPairing(WebSocketSession newSession) throws IOException {
        if (isLonelySession()) {
            WebSocketSession lonelySession = activeGames.getLast().getValue0();
            activeGames.removeLast();
            activeGames.add(new Triplet<>(lonelySession, newSession, GameFactory.createNewGame()));

            lonelySession.sendMessage(jsonMessage(SERVER_GAME_UPDATE_GAME_STARTED, "1st player"));
            newSession.sendMessage(jsonMessage(SERVER_GAME_UPDATE_GAME_STARTED, "2nd player"));
        } else {
            activeGames.add(new Triplet<>(newSession, null, null));
        }

        logger.info("Summarize of current sessions: {}", activeGames);
    }

    private boolean isLonelySession() {
        if (activeGames.isEmpty()) {
            return false;
        } else {
            return activeGames.getLast().getValue1() == null;
        }
    }

    private void handleGameUpdate(WebSocketSession session, JSONObject jsonMessage) throws IOException {
        String clientChosenSquareValue = jsonMessage.getString(CLIENT_GAME_UPDATE_CHOSEN_SQUARE_VALUE);
        String clientChosenSquareNumber = jsonMessage.getString(CLIENT_GAME_UPDATE_CHOSEN_SQUARE_NUMBER);

        WebSocketSession pairedSession = findPairedSession(session);
        pairedSession.sendMessage(jsonMessage(SERVER_GAME_UPDATE_STATUS, clientChosenSquareNumber));

        Game currentGame = getCurrentGame(session);
        currentGame.updateState(clientChosenSquareNumber, clientChosenSquareValue);
        Game.State state = currentGame.determineGameState();

        switch (state) {
            case FIRST_PLAYER_WON -> {
                session.sendMessage(jsonMessage(SERVER_GAME_UPDATE_GAME_ENDED, Game.State.FIRST_PLAYER_WON.message()));
                pairedSession.sendMessage(jsonMessage(SERVER_GAME_UPDATE_GAME_ENDED, Game.State.FIRST_PLAYER_WON.message()));
            }
            case SECOND_PLAYER_WON -> {
                session.sendMessage(jsonMessage(SERVER_GAME_UPDATE_GAME_ENDED, Game.State.SECOND_PLAYER_WON.message()));
                pairedSession.sendMessage(jsonMessage(SERVER_GAME_UPDATE_GAME_ENDED, Game.State.SECOND_PLAYER_WON.message()));
            }
            case UNRESOLVED -> {
                session.sendMessage(jsonMessage(SERVER_GAME_UPDATE_GAME_ENDED, Game.State.UNRESOLVED.message()));
                pairedSession.sendMessage(jsonMessage(SERVER_GAME_UPDATE_GAME_ENDED, Game.State.UNRESOLVED.message()));
            }
        }
    }

    private Game getCurrentGame(WebSocketSession session) {
        for (Triplet<WebSocketSession, WebSocketSession, Game> activeGame : activeGames) {
            if (activeGame.getValue0() == session || activeGame.getValue1() == session) {
                return activeGame.getValue2();
            }
        }

        throw new GameException("No current game found");
    }

    private void handleHeartbeat(WebSocketSession callingSession) throws IOException {
        sessionsLastHeartbeat.put(callingSession, System.currentTimeMillis());
        callingSession.sendMessage(jsonMessage(SERVER_SESSION_STATUS_UPDATE_HEARTBEAT, String.valueOf(System.currentTimeMillis())));
    }

    private void handleMessageForwarding(WebSocketSession messagingSession, JSONObject message) throws IOException {
        WebSocketSession pairedSession = findPairedSession(messagingSession);
        if (pairedSession != null) {
            pairedSession.sendMessage(jsonMessage(SERVER_MESSAGE_NEW_MESSAGE, message.getString(CLIENT_MESSAGE_NEW_MESSAGE)));
        }
    }

    private void handleMessageConfirmationForwarding(WebSocketSession messagingSession) throws IOException {
        WebSocketSession pairedSession = findPairedSession(messagingSession);
        if (pairedSession != null) {
            pairedSession.sendMessage(jsonMessage(SERVER_MESSAGE_CLIENT_RECEIVED_MESSAGE_CONFIRMATION, "Ok"));
        }
    }

    private void handleGameStatusUpdateConfirmationForwarding(WebSocketSession messagingSession) throws IOException {
        WebSocketSession pairedSession = findPairedSession(messagingSession);
        if (pairedSession != null) {
            pairedSession.sendMessage(jsonMessage(SERVER_MESSAGE_CLIENT_RECEIVED_GAME_STATUS_CHANGE_CONFIRMATION, "Ok"));
        }
    }

    private void handleDisconnection(WebSocketSession disconnectingSession) throws IOException {
        WebSocketSession pairedSession = findPairedSession(disconnectingSession);

        if (pairedSession != null) {
            if (pairedSession.isOpen()) {
                pairedSession.sendMessage(jsonMessage(SERVER_SESSION_STATUS_UPDATE_PAIRED_SESSION_DISCONNECTED, "Your opponent has disconnected"));
                pairedSession.close();
            }

            removeSession(disconnectingSession);
            removeSession(pairedSession);
            removeActiveGame(disconnectingSession);
        } else {
            removeSession(disconnectingSession);
            removeLonelyFromGames(disconnectingSession);
        }
    }

    private WebSocketSession findPairedSession(WebSocketSession messagingSession) {
        for (Triplet<WebSocketSession, WebSocketSession, Game> activeGame : activeGames) {
            if (activeGame.getValue0() == messagingSession) {
                return activeGame.getValue1();
            }
            if (activeGame.getValue1() == messagingSession) {
                return activeGame.getValue0();
            }
        }

        return null;
    }

    private void removeActiveGame(WebSocketSession disconnectingSession) {
        for (Triplet<WebSocketSession, WebSocketSession, Game> activeGame : activeGames) {
            if (activeGame.getValue0() == disconnectingSession || activeGame.getValue1() == disconnectingSession) {
                activeGames.remove(activeGame);
                break;
            }
        }
    }

    private void removeLonelyFromGames(WebSocketSession disconnectingSession) {
        for (Triplet<WebSocketSession, WebSocketSession, Game> activeGame : activeGames) {
            if (activeGame.getValue0() == disconnectingSession) {
                activeGames.remove(activeGame);
                break;
            }
        }
    }

    private void addSession(WebSocketSession newSession) {
        sessionsLastHeartbeat.put(newSession, System.currentTimeMillis());
    }

    private void removeSession(WebSocketSession disconnectingSession) {
        sessionsLastHeartbeat.remove(disconnectingSession);
    }

    private TextMessage jsonMessage(String messageKey, String messageValue) {
        JSONObject jsonMessage = new JSONObject().put(messageKey, messageValue);
        return new TextMessage(jsonMessage.toString());
    }
}
