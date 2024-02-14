package pl.lukasz94w;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
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
import pl.lukasz94w.commons.GameServerUtil;
import pl.lukasz94w.configuration.WebSocketServerConfig;
import pl.lukasz94w.exception.GameException;
import pl.lukasz94w.exception.GameServerAccessDeniedException;
import pl.lukasz94w.game.Game;
import pl.lukasz94w.game.GameFactory;
import pl.lukasz94w.player.Player;
import pl.lukasz94w.player.PlayerFactory;
import pl.lukasz94w.player.PlayerHolder;
import pl.lukasz94w.tictactoe.Tictactoe;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static pl.lukasz94w.message.Client.*;
import static pl.lukasz94w.message.Server.*;
import static pl.lukasz94w.tictactoe.Tictactoe.State.ONGOING;

@AllArgsConstructor
public class GameServer extends TextWebSocketHandler {

    private final Logger logger = LoggerFactory.getLogger(GameServer.class);

    private final List<Game> games;

    private final RestTemplate restTemplate;

    private WebSocketServerConfig serverConfig;

    private GameServerUtil gameServerUtil;

    public GameServer(WebSocketServerConfig serverConfig, GameServerUtil gameServerUtil) {
        this.serverConfig = serverConfig;
        this.gameServerUtil = gameServerUtil;
        this.games = new CopyOnWriteArrayList<>();
        this.restTemplate = new RestTemplate();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        HttpHeaders authenticationHeaders = gameServerUtil.removeWebSocketHeaders(session.getHandshakeHeaders());

        try {
            verifyMaxSessionsNumber();
            String userName = verifyAuthenticationAndGetUserName(authenticationHeaders);
            verifyIfPlayerAlreadyHaveAGameActive(userName);
            acceptSession(session, userName);
        } catch (GameServerAccessDeniedException exception) {
            rejectSession(session, exception.getMessage());
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

    private void verifyMaxSessionsNumber() {
        if (games.size() >= serverConfig.maxNumberOfGames) {
            throw new GameServerAccessDeniedException("Maximum number of active sessions exceeded. Try again later");
        }
    }

    // catch original request containing session cookie and send it to validation
    // endpoint to check whether the session exist for sent cookie
    private String verifyAuthenticationAndGetUserName(HttpHeaders authenticationHeaders) {
        HttpEntity<String> httpEntity = new HttpEntity<>(authenticationHeaders);

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(serverConfig.getUserNameUrl, HttpMethod.GET, httpEntity, String.class);
        } catch (HttpClientErrorException e) {
            logger.info("Unauthorized attempt of connection"); // ip address could be added here (passed from handshake interceptor f.e.)
            throw new GameServerAccessDeniedException("User unauthenticated");
        }

        HttpStatusCode responseStatusCode = response.getStatusCode();
        assert (responseStatusCode.equals(HttpStatus.OK));
        String authCookie = gameServerUtil.extractAuthCookie(authenticationHeaders);
        logger.info("Request successfully validated for cookie: {}. Response status: {}", authCookie, responseStatusCode);
        return response.getBody();
    }

    private void verifyIfPlayerAlreadyHaveAGameActive(String userName) {
        games.stream()
                .flatMap(game -> Stream.of(game.getFirstPlayer().getName(), game.getSecondPlayer().getName()))
                .filter(name -> name.equals(userName))
                .findFirst()
                .ifPresent(name -> {
                    throw new GameServerAccessDeniedException("There can only be one game per player");
                });
    }

    private void acceptSession(WebSocketSession session, String playerName) {
        try {
            handlePlayersPairing(session, playerName);
        } catch (Exception e) {
            logger.error("Exception in acceptSession: {}", ExceptionUtils.getStackTrace(e));
        }
        logger.info("Server connection opened with session id: {}. Player name: {}", session.getId(), playerName);
    }

    private void rejectSession(WebSocketSession session, String rejectionReason) {
        logger.info("Session {} rejected, reason: {}", session.getId(), rejectionReason);

        try {
            session.sendMessage(gameServerUtil.jsonMessage(SERVER_SESSION_STATUS_UPDATE_SESSION_REJECTED, rejectionReason));

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

    private void handlePlayersPairing(WebSocketSession session, String playerName) throws IOException {
        if (isGameWithLonelyPlayer()) {
            Game gameWithLonelyPlayer = games.getLast();
            gameWithLonelyPlayer.attachSecondPlayer(PlayerFactory.createPlayer(session, playerName));
            gameWithLonelyPlayer.getFirstPlayer().getSession().sendMessage(gameServerUtil.jsonMessage(SERVER_GAME_UPDATE_GAME_STARTED, "1st player"));
            session.sendMessage(gameServerUtil.jsonMessage(SERVER_GAME_UPDATE_GAME_STARTED, "2nd player"));
        } else {
            games.add(GameFactory.createGame(PlayerFactory.createPlayer(session, playerName)));
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
        Optional<Player> opponent = findOptionalOpponent(disconnectingSession);

        if (opponent.isPresent()) {
            Player existingOpponent = opponent.get();
            WebSocketSession existingOpponentSession = existingOpponent.getSession();
            if (!(existingOpponent instanceof PlayerHolder) && existingOpponentSession.isOpen()) {
                existingOpponentSession.sendMessage(gameServerUtil.jsonMessage(SERVER_SESSION_STATUS_UPDATE_PAIRED_SESSION_DISCONNECTED, "Your opponent has disconnected"));
                existingOpponentSession.close();
            }
        }

        removeGame(disconnectingSession);
    }

    private void forwardGameUpdateToOpponent(WebSocketSession session, JSONObject jsonMessage) throws IOException {
        String clientChosenSquareNumber = jsonMessage.getString(CLIENT_GAME_UPDATE_CHOSEN_SQUARE_NUMBER);

        // Send refreshed game status to opponent. Game status in the server will be updated only
        // after getting the confirmation of receiving the game status update from the opponent.
        WebSocketSession opponentSession = findOpponent(session).getSession();
        opponentSession.sendMessage(gameServerUtil.jsonMessage(SERVER_GAME_UPDATE_GAME_CHANGED, clientChosenSquareNumber));
    }

    private Player findOpponent(WebSocketSession messagingSession) {
        return games.stream()
                .filter(game -> game.getFirstPlayer().getSession().equals(messagingSession) || game.getSecondPlayer().getSession().equals(messagingSession))
                .map(game -> game.getFirstPlayer().getSession().equals(messagingSession) ? game.getSecondPlayer() : game.getFirstPlayer())
                .findFirst()
                .orElseThrow(() -> new GameException("No opponent found for messaging session: " + messagingSession));
    }

    private Optional<Player> findOptionalOpponent(WebSocketSession messagingSession) {
        return games.stream()
                .filter(game -> game.getFirstPlayer().getSession().equals(messagingSession) || game.getSecondPlayer().getSession().equals(messagingSession))
                .map(game -> game.getFirstPlayer().getSession().equals(messagingSession) ? game.getSecondPlayer() : game.getFirstPlayer())
                .findFirst();
    }

    private void handleHeartbeat(WebSocketSession callingSession) throws IOException {
        Player sessionRelatedPlayer = games.stream()
                .filter(game -> game.getFirstPlayer().getSession().equals(callingSession) || game.getSecondPlayer().getSession().equals(callingSession))
                .map(game -> game.getFirstPlayer().getSession().equals(callingSession) ? game.getFirstPlayer() : game.getSecondPlayer())
                .findFirst()
                .orElseThrow(() -> new GameException("No related session found"));

        sessionRelatedPlayer.updateLastHeartbeat();
        callingSession.sendMessage(gameServerUtil.jsonMessage(SERVER_SESSION_STATUS_UPDATE_HEARTBEAT, String.valueOf(System.currentTimeMillis())));
    }

    private void forwardMessageToOpponent(WebSocketSession messagingSession, JSONObject message) throws IOException {
        Player opponent = findOpponent(messagingSession);
        if (!(opponent instanceof PlayerHolder)) {
            opponent.getSession().sendMessage(gameServerUtil.jsonMessage(SERVER_MESSAGE_OPPONENT_MESSAGE, message.getString(CLIENT_MESSAGE_PLAYER_MESSAGE)));
        }
    }

    private Game findGameRelated(WebSocketSession session) {
        return games.stream()
                .filter(game -> game.getFirstPlayer().getSession().equals(session) || game.getSecondPlayer().getSession().equals(session))
                .findFirst()
                .orElseThrow(() -> new GameException("No related game found"));
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

        opponent.getSession().sendMessage(gameServerUtil.jsonMessage(SERVER_GAME_OPPONENT_RECEIVED_GAME_STATUS_CHANGE_CONFIRMATION, "Ok"));

        String playerChosenSquareValue = jsonMessage.getString(CLIENT_GAME_UPDATE_CHOSEN_SQUARE_VALUE);
        String playerChosenSquareNumber = jsonMessage.getString(CLIENT_GAME_UPDATE_CHOSEN_SQUARE_NUMBER);

        Tictactoe tictactoe = game.getTictactoe();
        tictactoe.updateState(playerChosenSquareNumber, playerChosenSquareValue);
        Tictactoe.State state = tictactoe.determineNewTictactoeState();

        if (!state.equals(ONGOING)) {
            informPlayers(confirmingPlayer, state, opponent);
            informHistoryService(game, state);
        }
    }

    private void informPlayers(Player confirmingPlayer, Tictactoe.State state, Player opponent) throws IOException {
        confirmingPlayer.getSession().sendMessage(gameServerUtil.jsonMessage(SERVER_GAME_UPDATE_GAME_ENDED, state.message()));
        opponent.getSession().sendMessage(gameServerUtil.jsonMessage(SERVER_GAME_UPDATE_GAME_ENDED, state.message()));
    }

    private void informHistoryService(Game game, Tictactoe.State state) throws JsonProcessingException {
        restTemplate.postForEntity(serverConfig.saveGameUrl, gameServerUtil.getRequestHttpEntity(game, state), String.class);
    }

    private void removeGame(WebSocketSession disconnectingSession) {
        games.removeIf(game -> game.getFirstPlayer().getSession().equals(disconnectingSession) || game.getSecondPlayer().getSession().equals(disconnectingSession));
    }
}
