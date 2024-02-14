package pl.lukasz94w.commons;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import pl.lukasz94w.game.Game;
import pl.lukasz94w.request.FinishedGameData;
import pl.lukasz94w.tictactoe.Tictactoe;

import java.util.List;

@Component
public class GameServerUtil {

    // Method which removes WebSocket typical headers. Because request is send to http endpoint (not WebSocket endpoint) it's good
    // practice to remove original WebSocket headers which were received from the WebSocket client. Note: I tested and the 200 HTTP
    // status is sent back even if these headers are not used (of course session cookie must be valid). Basic http header (SESSION)
    // survive filtering.
    public HttpHeaders removeWebSocketHeaders(HttpHeaders originalHeaders) {
        List<String> webSocketHeaders = List.of("connection", "upgrade", "sec-websocket-version", "sec-websocket-key", "sec-websocket-extensions");

        HttpHeaders writableHttpHeaders = HttpHeaders.writableHttpHeaders(originalHeaders);
        webSocketHeaders.forEach(writableHttpHeaders::remove);

        return writableHttpHeaders;
    }

    public String extractAuthCookie(HttpHeaders requestHeaders) {
        @SuppressWarnings("ConstantConditions")
        String cookieHeader = requestHeaders.get("cookie").getFirst(); // null pointer never happens here due to checking if there is a cookie in API gateway
        return cookieHeader.replace("SESSION=", "");
    }

    public TextMessage jsonMessage(String messageKey, String messageValue) {
        JSONObject jsonMessage = new JSONObject().put(messageKey, messageValue);
        return new TextMessage(jsonMessage.toString());
    }

    public HttpEntity<String> getRequestHttpEntity(Game game, Tictactoe.State state) throws JsonProcessingException {
        FinishedGameData finishedGameData = getFinishedGameData(game, state);

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        String requestBody = objectMapper.writeValueAsString(finishedGameData);

        return new HttpEntity<>(requestBody, requestHeaders);
    }

    private FinishedGameData getFinishedGameData(Game game, Tictactoe.State state) {
        String winnerName = switch (state) {
            case FIRST_PLAYER_WON -> game.getFirstPlayer().getName();
            case SECOND_PLAYER_WON -> game.getSecondPlayer().getName();
            default -> "";
        };

        Tictactoe tictactoe = game.getTictactoe();
        return new FinishedGameData(game.getFirstPlayer().getName(), game.getSecondPlayer().getName(), winnerName, tictactoe.getGameStartedUTC(), tictactoe.getGameEndedUTC(), tictactoe.getNumberOfWinningMovements());
    }
}
