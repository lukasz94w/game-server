package pl.lukasz94w.player;

import org.springframework.web.socket.WebSocketSession;

public class PlayerFactory {

    private PlayerFactory() {
    }

    public static Player createPlayer(WebSocketSession session, String authCookie) {
        return new Player(session, authCookie);
    }

    public static Player createPlayerHolder() {
        return new PlayerHolder();
    }
}