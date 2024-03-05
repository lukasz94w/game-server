package pl.lukasz94w;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import pl.lukasz94w.configuration.WebSocketServerConfig;
import pl.lukasz94w.dto.http.request.FinishedGameData;
import pl.lukasz94w.exception.GameException;
import pl.lukasz94w.exception.GameServerAccessDeniedException;
import pl.lukasz94w.exception.MissingParameterException;
import pl.lukasz94w.game.Game;
import pl.lukasz94w.game.GameFactory;
import pl.lukasz94w.player.Player;
import pl.lukasz94w.player.PlayerFactory;
import pl.lukasz94w.tictactoe.Tictactoe;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static pl.lukasz94w.dto.websocket.common.JsonKey.DATA;
import static pl.lukasz94w.dto.websocket.common.JsonKey.MESSAGE_TYPE;
import static pl.lukasz94w.dto.websocket.incoming.ClientMessageTypeValue.*;
import static pl.lukasz94w.dto.websocket.outgoing.ServerMessageTypeValue.*;
import static pl.lukasz94w.tictactoe.Tictactoe.Result.ONGOING;

public class GameServer extends TextWebSocketHandler {

    private final Logger logger = LoggerFactory.getLogger(GameServer.class);

    private final List<Game> games;

    private final WebSocketServerConfig webSocketServerConfig;

    private final RestTemplate historyServiceClient;

    private Player lonelyPlayer;

    public GameServer(WebSocketServerConfig webSocketServerConfig, RestTemplate historyServiceClient) {
        this.webSocketServerConfig = webSocketServerConfig;
        this.historyServiceClient = historyServiceClient;
        games = new CopyOnWriteArrayList<>();
        lonelyPlayer = null;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userName = session.getHandshakeHeaders().getFirst("username"); // coming from api-gateway-service

        try {
            verifyMaxSessionsNumber();
            verifyIfPLayerIsLonelyPlayer(userName);
            verifyIfPlayerAlreadyHaveAGame(userName);
            acceptSession(session, userName);
        } catch (GameServerAccessDeniedException exception) {
            rejectSession(session, exception.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession disconnectingSession, CloseStatus status) {
        try {
            if (isCallingSessionLonelyPlayerSession(disconnectingSession)) {
                handleLonelyPlayerDisconnection();
            } else {
                handleActivePlayerDisconnection(disconnectingSession);
            }
            logger.info("Server connection closed: {}, session id: {}", status, disconnectingSession.getId());
        } catch (Exception e) {
            logger.error("Exception in afterConnectionClosed: {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        JSONObject jsonMessage = new JSONObject(message.getPayload());
        String messageType = validateAndGetMessageType(jsonMessage);

        try {
            switch (messageType) {
                case PLAYER_MESSAGE -> forwardMessageToOpponent(session, jsonMessage);
                case GAME_UPDATE -> forwardGameUpdateToOpponent(session, jsonMessage);
                case HEARTBEAT -> updateLastHeartbeat(session);
                case PLAYER_RECEIVED_GAME_UPDATE_CONFIRMATION -> updateGameStatus(session, jsonMessage);
                default -> logger.error("Unknown type of message from session: {}", session.getId());
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
        checkLonelyPlayerSession(currentTimestamp);
        checkActiveGamesSessions(currentTimestamp);
    }

    private void verifyMaxSessionsNumber() {
        if (games.size() >= webSocketServerConfig.maxNumberOfGames) {
            throw new GameServerAccessDeniedException("Maximum number of active sessions exceeded. Try again later");
        }
    }

    private void verifyIfPLayerIsLonelyPlayer(String userName) {
        if (isLonelyPlayer() && lonelyPlayer.getName().equals(userName)) {
            throw new GameServerAccessDeniedException("Player already waiting in lobby!");
        }
    }

    private void verifyIfPlayerAlreadyHaveAGame(String userName) {
        games.stream().flatMap(game -> Stream.of(game.getFirstPlayer().getName(), game.getSecondPlayer().getName())).filter(name -> name.equals(userName)).findFirst().ifPresent(name -> {
            throw new GameServerAccessDeniedException("There can only be one game per player");
        });
    }

    private void acceptSession(WebSocketSession session, String playerName) {
        try {
            handlePlayersPairing(session, playerName);
            logger.info("Server connection opened, session id: {}, player name: {}", session.getId(), playerName);
        } catch (Exception e) {
            logger.error("Exception in acceptSession: {}", ExceptionUtils.getStackTrace(e));
        }
    }

    private void rejectSession(WebSocketSession session, String rejectionReason) {
        logger.info("Session {} rejected, reason: {}", session.getId(), rejectionReason);

        try {
            session.sendMessage(buildTextMessage(SESSION_REJECTED, rejectionReason));
            ensureSessionClosed(session);
        } catch (Exception e) {
            logger.error("Exception in rejectSession: {}", ExceptionUtils.getStackTrace(e));
        }
    }

    private String validateAndGetMessageType(JSONObject payload) {
        if (!payload.has(MESSAGE_TYPE)) {
            throw new MissingParameterException("Missing message type header");
        }

        return payload.getString(MESSAGE_TYPE);
    }

    private void handlePlayersPairing(WebSocketSession session, String playerName) throws IOException {
        if (isLonelyPlayer()) {
            Player firstPlayer = PlayerFactory.createPlayer(lonelyPlayer.getSession(), lonelyPlayer.getName()); // shallow copy
            clearLonelyPlayerReference();
            Player secondPlayer = PlayerFactory.createPlayer(session, playerName);
            games.add(GameFactory.createGame(firstPlayer, secondPlayer));

            firstPlayer.getSession().sendMessage(buildTextMessage(GAME_STARTED, "1st player"));
            secondPlayer.getSession().sendMessage(buildTextMessage(GAME_STARTED, "2nd player"));
        } else {
            lonelyPlayer = PlayerFactory.createPlayer(session, playerName);
        }
    }

    private void checkLonelyPlayerSession(long currentTimestamp) {
        if (isLonelyPlayer() && isHeartbeatExpired(currentTimestamp).test(lonelyPlayer)) {
            closeInactiveSession(lonelyPlayer);
        }
    }

    private void checkActiveGamesSessions(long currentTimestamp) {
        games.stream().flatMap(game -> Stream.of(game.getFirstPlayer(), game.getSecondPlayer())).filter(isHeartbeatExpired(currentTimestamp)).forEach(this::closeInactiveSession);
    }

    private Predicate<Player> isHeartbeatExpired(long currentTimestamp) {
        return player -> currentTimestamp - player.getLastHeartbeat() > webSocketServerConfig.requiredHeartbeatFrequency;
    }

    private void handleLonelyPlayerDisconnection() {
        clearLonelyPlayerReference();
    }

    private void handleActivePlayerDisconnection(WebSocketSession disconnectingSession) throws IOException {
        // if it's not a lonely session player server should: inform second player about disconnection and remove the game
        Optional<WebSocketSession> optionalOpponentSession = findOptionalOpponentSession(disconnectingSession);
        if (optionalOpponentSession.isPresent()) {
            WebSocketSession opponentSession = optionalOpponentSession.get();
            opponentSession.sendMessage(buildTextMessage(PAIRED_SESSION_DISCONNECTED, "Your opponent has disconnected"));
        }

        removeGame(disconnectingSession);
    }

    private void closeInactiveSession(Player player) {
        try {
            logger.info("Inactive session detected: {}, closing it...", player.getSession().getId());
            player.getSession().close(); // triggers afterConnectionClose()
        } catch (IOException e) {
            logger.error("Exception in inactiveSessionsCleaner, during closing idle session with id: {}", player.getSession().getId());
        }
    }

    // Send refreshed game status to opponent. Game status in the server will be updated only
    // after getting the confirmation of receiving the game status update from the opponent.
    private void forwardGameUpdateToOpponent(WebSocketSession session, JSONObject payload) throws IOException {
        String opponentChosenSquareNumber = payload.getString(DATA);
        findOpponentSession(session).sendMessage(buildTextMessage(GAME_UPDATED, opponentChosenSquareNumber));
    }

    private WebSocketSession findOpponentSession(WebSocketSession messagingSession) {
        return games.stream().filter(game -> game.getFirstPlayer().getSession().equals(messagingSession) || game.getSecondPlayer().getSession().equals(messagingSession)).map(game -> game.getFirstPlayer().getSession().equals(messagingSession) ? game.getSecondPlayer().getSession() : game.getFirstPlayer().getSession()).findFirst().orElseThrow(() -> new GameException("No opponent session found for messaging session: " + messagingSession));
    }

    private Optional<WebSocketSession> findOptionalOpponentSession(WebSocketSession messagingSession) {
        return games.stream().filter(game -> game.getFirstPlayer().getSession().equals(messagingSession) || game.getSecondPlayer().getSession().equals(messagingSession)).map(game -> game.getFirstPlayer().getSession().equals(messagingSession) ? game.getSecondPlayer().getSession() : game.getFirstPlayer().getSession()).findFirst();
    }

    private void updateLastHeartbeat(WebSocketSession callingSession) throws IOException {
        if (isCallingSessionLonelyPlayerSession(callingSession)) {
            updateLonelyPlayerLastHeartbeat();
        } else {
            updateActivePlayerLastHeartbeat(callingSession);
        }
    }

    private void updateLonelyPlayerLastHeartbeat() {
        lonelyPlayer.updateLastHeartbeat();
    }

    private void updateActivePlayerLastHeartbeat(WebSocketSession callingSession) throws IOException {
        Player sessionRelatedPlayer = games.stream().filter(game -> game.getFirstPlayer().getSession().equals(callingSession) || game.getSecondPlayer().getSession().equals(callingSession)).map(game -> game.getFirstPlayer().getSession().equals(callingSession) ? game.getFirstPlayer() : game.getSecondPlayer()).findFirst().orElseThrow(() -> new GameException("No related session found"));
        sessionRelatedPlayer.updateLastHeartbeat();
        callingSession.sendMessage(buildTextMessage(HEARTBEAT_RECEIVED_CONFIRMATION, String.valueOf(System.currentTimeMillis())));
    }

    private void forwardMessageToOpponent(WebSocketSession messagingSession, JSONObject payload) throws IOException {
        String actualMessage = payload.getString(DATA);
        findOpponentSession(messagingSession).sendMessage(buildTextMessage(OPPONENT_MESSAGE, actualMessage));
    }

    private Game findGameRelated(WebSocketSession session) {
        return games.stream().filter(game -> game.getFirstPlayer().getSession().equals(session) || game.getSecondPlayer().getSession().equals(session)).findFirst().orElseThrow(() -> new GameException("No related game found"));
    }

    // After receiving confirmation from the opponent: 1. send a confirmation to the player about
    // receiving the message, 2. update game status in the server, 3. determine new state, 4. (optional):
    // finish the game and send history to history-service.
    private void updateGameStatus(WebSocketSession confirmingSession, JSONObject jsonMessage) throws IOException {
        Game game = findGameRelated(confirmingSession);
        Player firstPlayer = game.getFirstPlayer();
        Player secondPlayer = game.getSecondPlayer();

        Player confirmingPlayer = firstPlayer.getSession().equals(confirmingSession) ? firstPlayer : secondPlayer;
        Player opponent = confirmingPlayer.equals(firstPlayer) ? secondPlayer : firstPlayer;

        opponent.getSession().sendMessage(buildTextMessage(OPPONENT_RECEIVED_GAME_UPDATE_CONFIRMATION, "Opponent received the update"));

        String playerChosenSquareValueAndNumber = jsonMessage.getString(DATA);
        String playerChosenSquareValue = playerChosenSquareValueAndNumber.substring(0, 1);
        String playerChosenSquareNumber = playerChosenSquareValueAndNumber.substring(1, 2);

        Tictactoe tictactoe = game.getTictactoe();
        tictactoe.updateState(playerChosenSquareNumber, playerChosenSquareValue);
        Tictactoe.Result result = tictactoe.checkWhetherGameEnded();

        if (!result.equals(ONGOING)) {
            informPlayersAboutFinishedGame(confirmingPlayer, result, opponent);
            informHistoryServiceAboutFinishedGame(game, result);
        }
    }

    private boolean isLonelyPlayer() {
        return lonelyPlayer != null;
    }

    private boolean isCallingSessionLonelyPlayerSession(WebSocketSession callingSession) {
        return isLonelyPlayer() && lonelyPlayer.getSession().equals(callingSession);
    }

    private void clearLonelyPlayerReference() {
        lonelyPlayer = null;
    }

    private void informPlayersAboutFinishedGame(Player confirmingPlayer, Tictactoe.Result result, Player opponent) throws IOException {
        confirmingPlayer.getSession().sendMessage(buildTextMessage(GAME_ENDED, result.message()));
        opponent.getSession().sendMessage(buildTextMessage(GAME_ENDED, result.message()));
    }

    private void informHistoryServiceAboutFinishedGame(Game game, Tictactoe.Result result) throws JsonProcessingException {
        historyServiceClient.postForEntity("", getRequestHttpEntity(game, result), String.class);
    }

    private void removeGame(WebSocketSession disconnectingSession) {
        games.removeIf(game -> game.getFirstPlayer().getSession().equals(disconnectingSession) || game.getSecondPlayer().getSession().equals(disconnectingSession));
    }

    private TextMessage buildTextMessage(String messageType, String messageData) {
        Map<String, String> message = Map.of(MESSAGE_TYPE, messageType, DATA, messageData);
        return new TextMessage(new JSONObject(message).toString());
    }

    private HttpEntity<String> getRequestHttpEntity(Game game, Tictactoe.Result result) throws JsonProcessingException {
        FinishedGameData finishedGameData = getFinishedGameData(game, result);

        logger.info("Game finished for players: {}, {}. Winner: {}. Sending game result to history-service", finishedGameData.getFirstPlayerName(), finishedGameData.getSecondPlayerName(), finishedGameData.getWinnerName());

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        String requestBody = objectMapper.writeValueAsString(finishedGameData);

        return new HttpEntity<>(requestBody, requestHeaders);
    }

    private FinishedGameData getFinishedGameData(Game game, Tictactoe.Result result) {
        String winnerName = switch (result) {
            case FIRST_PLAYER_WON -> game.getFirstPlayer().getName();
            case SECOND_PLAYER_WON -> game.getSecondPlayer().getName();
            default -> "";
        };

        Tictactoe tictactoe = game.getTictactoe();
        return new FinishedGameData(game.getFirstPlayer().getName(), game.getSecondPlayer().getName(), winnerName, tictactoe.getGameStartedUTC(), tictactoe.getGameEndedUTC(), tictactoe.getNumberOfWinningMovements());
    }

    // cleaning just in case rejected session haven't closed the session after 10 seconds since receiving rejection message
    private void ensureSessionClosed(WebSocketSession session) {
        try (ExecutorService executorService = Executors.newFixedThreadPool(1)) {
            executorService.submit(() -> {
                try {
                    Thread.sleep(10000);
                    if (session.isOpen()) {
                        session.close();
                    }
                } catch (Exception e) {
                    logger.error("Exception during closing the rejected session: {}", ExceptionUtils.getStackTrace(e));
                }
            });
        } catch (Exception e) {
            logger.error("Exception in ExecutorService: {}", ExceptionUtils.getStackTrace(e));
        }
    }
}