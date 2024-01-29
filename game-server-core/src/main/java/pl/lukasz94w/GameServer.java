package pl.lukasz94w;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import pl.lukasz94w.configuration.WebSocketServerConfig;
import pl.lukasz94w.exception.GameException;
import pl.lukasz94w.game.Game;
import pl.lukasz94w.game.GameFactory;
import pl.lukasz94w.player.Player;
import pl.lukasz94w.player.PlayerFactory;
import pl.lukasz94w.player.PlayerHolder;
import pl.lukasz94w.tictactoe.Tictactoe;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static pl.lukasz94w.message.Client.*;
import static pl.lukasz94w.message.Server.*;

public class GameServer extends TextWebSocketHandler {

    private final Logger logger = LoggerFactory.getLogger(GameServer.class);

    private final List<Game> games = new CopyOnWriteArrayList<>();

    private final WebSocketServerConfig serverConfig;

    public GameServer(WebSocketServerConfig webSocketServerConfig) {
        this.serverConfig = webSocketServerConfig;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        HttpHeaders handshakeHeaders = session.getHandshakeHeaders();
        String authCookie = extractAuthCookie(handshakeHeaders);

        if (games.size() >= serverConfig.maxNumberOfGames) {
            rejectSession(session, "Maximum number of active sessions exceeded. Try again later");
        } else if (!checkAuthentication(handshakeHeaders)) {
            rejectSession(session, "User unauthenticated");
        } else if (checkIfGameAlreadyExists(authCookie)) {
            rejectSession(session, "There can only be one game per session");
        } else {
            acceptSession(session, authCookie);
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
            if (jsonMessage.has(CLIENT_MESSAGE_PLAYER_MESSAGE)) {
                forwardMessageToOpponent(session, jsonMessage);
            } else if (jsonMessage.has(CLIENT_GAME_UPDATE_GAME_CHANGED)) {
                forwardGameUpdateToOpponent(session, jsonMessage);
            } else if (jsonMessage.has(CLIENT_SESSION_STATUS_UPDATE_HEARTBEAT)) {
                handleHeartbeat(session);
            } else if (jsonMessage.has(CLIENT_GAME_RECEIVED_GAME_STATUS_UPDATE_CONFIRMATION)) {
                updateGameStatus(session, jsonMessage);
            } else {
                logger.error("Unknown type of message from session: {}", session.getId());
            }
        } catch (Exception e) {
            logger.error("Exception in handleTextMessage: {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.error("Exception in handleTransportError: {}", ExceptionUtils.getStackTrace(exception));
    }

    @Scheduled(fixedDelayString = "${pl.lukasz94w.inactiveSessionsCheckingFrequency}")
    private void inactiveSessionsCleaner() {
        long currentTimestamp = System.currentTimeMillis();

        games.stream()
                .flatMap(game -> Stream.of(game.getFirstPlayer(), game.getSecondPlayer()))
                .filter(player -> currentTimestamp - player.getLastHeartbeat() > serverConfig.requiredHeartbeatFrequency)
                .forEach(inactivePlayer -> {
                    try {
                        logger.info("Inactive session detected: {}, closing it...", inactivePlayer.getSession().getId());
                        inactivePlayer.getSession().close(); // triggers afterConnectionClose()
                    } catch (IOException e) {
                        logger.error("Exception in inactiveSessionsCleaner, during closing idle session with id: {}", inactivePlayer.getSession().getId());
                    }
                });
    }

    private void acceptSession(WebSocketSession session, String authCookie) {
        try {
            handlePlayersPairing(session, authCookie);
        } catch (Exception e) {
            logger.error("Exception in acceptSession: {}", ExceptionUtils.getStackTrace(e));
        }

        logger.info("Server connection opened with session id: {}. Total games number: {}", session.getId(), games.size());
    }

    private void rejectSession(WebSocketSession session, String rejectionReason) {
        logger.info("Session {} rejected, reason: {}", session.getId(), rejectionReason);

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

    private void handlePlayersPairing(WebSocketSession session, String authCookie) throws IOException {
        if (isGameWithLonelyPlayer()) {
            Game gameWithLonelyPlayer = games.getLast();
            gameWithLonelyPlayer.attachSecondPlayer(PlayerFactory.createPlayer(session, authCookie));
            gameWithLonelyPlayer.getFirstPlayer().getSession().sendMessage(jsonMessage(SERVER_GAME_UPDATE_GAME_STARTED, "1st player"));
            session.sendMessage(jsonMessage(SERVER_GAME_UPDATE_GAME_STARTED, "2nd player"));
        } else {
            games.add(GameFactory.createGame(PlayerFactory.createPlayer(session, authCookie)));
        }
    }

    private boolean isGameWithLonelyPlayer() {
        if (games.isEmpty()) {
            return false;
        } else {
            return games.getLast().getSecondPlayer() instanceof PlayerHolder;
        }
    }

    private void handleDisconnection(WebSocketSession disconnectingSession) throws IOException {
        Player opponent = findOpponent(disconnectingSession);
        WebSocketSession opponentSession = opponent.getSession();

        if (!(opponent instanceof PlayerHolder) && opponentSession.isOpen()) {
            opponentSession.sendMessage(jsonMessage(SERVER_SESSION_STATUS_UPDATE_PAIRED_SESSION_DISCONNECTED, "Your opponent has disconnected"));
            opponentSession.close();
        }

        removeGame(disconnectingSession);
    }

    private void forwardGameUpdateToOpponent(WebSocketSession session, JSONObject jsonMessage) throws IOException {
        String clientChosenSquareNumber = jsonMessage.getString(CLIENT_GAME_UPDATE_CHOSEN_SQUARE_NUMBER);

        // Send refreshed game status to opponent. Game status in the server will be updated only
        // after getting the confirmation of receiving the game status update from the opponent.
        WebSocketSession opponentSession = findOpponent(session).getSession();
        opponentSession.sendMessage(jsonMessage(SERVER_GAME_UPDATE_GAME_CHANGED, clientChosenSquareNumber));
    }

    private Player findOpponent(WebSocketSession messagingSession) {
        return games.stream()
                .filter(game -> game.getFirstPlayer().getSession().equals(messagingSession) || game.getSecondPlayer().getSession().equals(messagingSession))
                .map(game -> game.getFirstPlayer().getSession().equals(messagingSession) ? game.getSecondPlayer() : game.getFirstPlayer())
                .findFirst()
                .orElseThrow(() -> new GameException("No opponent found"));
    }

    private Tictactoe findRelatedTictactoe(WebSocketSession session) {
        return games.stream()
                .filter(game -> game.getFirstPlayer().getSession().equals(session) || game.getSecondPlayer().getSession().equals(session))
                .map(Game::getTictactoe)
                .findFirst()
                .orElseThrow(() -> new GameException("No Tictactoe game found for the session: " + session.getId()));
    }

    private void handleHeartbeat(WebSocketSession callingSession) throws IOException {
        Player sessionRelatedPlayer = games.stream()
                .filter(game -> game.getFirstPlayer().getSession().equals(callingSession) || game.getSecondPlayer().getSession().equals(callingSession))
                .map(game -> game.getFirstPlayer().getSession().equals(callingSession) ? game.getFirstPlayer() : game.getSecondPlayer())
                .findFirst()
                .orElseThrow(() -> new GameException("No related session found"));

        sessionRelatedPlayer.updateLastHeartbeat();
        callingSession.sendMessage(jsonMessage(SERVER_SESSION_STATUS_UPDATE_HEARTBEAT, String.valueOf(System.currentTimeMillis())));
    }

    private void forwardMessageToOpponent(WebSocketSession messagingSession, JSONObject message) throws IOException {
        Player opponent = findOpponent(messagingSession);
        if (!(opponent instanceof PlayerHolder)) {
            opponent.getSession().sendMessage(jsonMessage(SERVER_MESSAGE_OPPONENT_MESSAGE, message.getString(CLIENT_MESSAGE_PLAYER_MESSAGE)));
        }
    }

    // After receiving confirmation from the opponent: 1. send a confirmation to the player about
    // receiving the message, 2. update game status in the server, 3. determine new state.
    private void updateGameStatus(WebSocketSession confirmingSession, JSONObject jsonMessage) throws IOException {
        Player opponent = findOpponent(confirmingSession);
        WebSocketSession opponentSession = opponent.getSession();

        if (!(opponent instanceof PlayerHolder)) {
            opponentSession.sendMessage(jsonMessage(SERVER_GAME_OPPONENT_RECEIVED_GAME_STATUS_CHANGE_CONFIRMATION, "Ok"));
        }

        String clientChosenSquareValue = jsonMessage.getString(CLIENT_GAME_UPDATE_CHOSEN_SQUARE_VALUE);
        String clientChosenSquareNumber = jsonMessage.getString(CLIENT_GAME_UPDATE_CHOSEN_SQUARE_NUMBER);

        Tictactoe tictactoe = findRelatedTictactoe(confirmingSession);
        tictactoe.updateState(clientChosenSquareNumber, clientChosenSquareValue);
        Tictactoe.State state = tictactoe.determineNewTictactoeState();

        switch (state) {
            case FIRST_PLAYER_WON -> {
                confirmingSession.sendMessage(jsonMessage(SERVER_GAME_UPDATE_GAME_ENDED, Tictactoe.State.FIRST_PLAYER_WON.message()));
                opponentSession.sendMessage(jsonMessage(SERVER_GAME_UPDATE_GAME_ENDED, Tictactoe.State.FIRST_PLAYER_WON.message()));
            }
            case SECOND_PLAYER_WON -> {
                confirmingSession.sendMessage(jsonMessage(SERVER_GAME_UPDATE_GAME_ENDED, Tictactoe.State.SECOND_PLAYER_WON.message()));
                opponentSession.sendMessage(jsonMessage(SERVER_GAME_UPDATE_GAME_ENDED, Tictactoe.State.SECOND_PLAYER_WON.message()));
            }
            case UNRESOLVED -> {
                confirmingSession.sendMessage(jsonMessage(SERVER_GAME_UPDATE_GAME_ENDED, Tictactoe.State.UNRESOLVED.message()));
                opponentSession.sendMessage(jsonMessage(SERVER_GAME_UPDATE_GAME_ENDED, Tictactoe.State.UNRESOLVED.message()));
            }
        }
    }

    private void removeGame(WebSocketSession disconnectingSession) {
        games.removeIf(game -> game.getFirstPlayer().getSession().equals(disconnectingSession) || game.getSecondPlayer().getSession().equals(disconnectingSession));
    }

    // catch original request containing session cookie and send it to validation
    // endpoint to check whether the session exist for sent cookie
    private boolean checkAuthentication(HttpHeaders handshakeHeaders) {
        HttpEntity<String> httpEntity = new HttpEntity<>(removeWebSocketHeaders(handshakeHeaders));

        ResponseEntity<String> responseEntity;
        try {
            responseEntity = new RestTemplate().exchange("http://localhost:8093/api/v1/auth/verifySessionActive", HttpMethod.GET, httpEntity, String.class);
        } catch (HttpClientErrorException e) {
            logger.info("Unauthorized attempt of connection"); // ip address could be added here (passed from handshake interceptor f.e.)
            return false;
        }

        HttpStatusCode responseStatusCode = responseEntity.getStatusCode();
        assert (responseStatusCode.equals(HttpStatus.OK));
        String authCookie = extractAuthCookie(handshakeHeaders);
        logger.info("Request successfully validated for cookie: {}. Response status: {}", authCookie, responseStatusCode);
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

    private String extractAuthCookie(HttpHeaders requestHeaders) {
        @SuppressWarnings("ConstantConditions")
        String cookieHeader = requestHeaders.get("cookie").getFirst(); // null pointer never happens here due to checking if there is a cookie in API gateway
        return cookieHeader.replace("SESSION=", "");
    }

    private boolean checkIfGameAlreadyExists(String cookie) {
        return games.stream()
                .flatMap(game -> Stream.of(game.getFirstPlayer().getAuthCookie(), game.getSecondPlayer().getAuthCookie()))
                .anyMatch(authCookie -> authCookie.equals(cookie));
    }

    private TextMessage jsonMessage(String messageKey, String messageValue) {
        JSONObject jsonMessage = new JSONObject().put(messageKey, messageValue);
        return new TextMessage(jsonMessage.toString());
    }
}
