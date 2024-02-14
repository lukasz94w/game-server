package pl.lukasz94w.player;

import lombok.Getter;
import org.springframework.web.socket.WebSocketSession;

@Getter
public class Player {

    protected final WebSocketSession session;

    protected final String name;

    protected Long lastHeartbeat;

    Player(WebSocketSession session, String name) {
        this.session = session;
        this.name = name;
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public void updateLastHeartbeat() {
        lastHeartbeat = System.currentTimeMillis();
    }
}
