package pl.lukasz94w.player;

import org.springframework.web.socket.WebSocketSession;

public class Player {
    protected final WebSocketSession session;
    protected final String authCookie;
    protected Long lastHeartbeat;

    Player(WebSocketSession session, String authCookie) {
        this.session = session;
        this.lastHeartbeat = System.currentTimeMillis();
        this.authCookie = authCookie;
    }

    public void updateLastHeartbeat() {
        lastHeartbeat = System.currentTimeMillis();
    }

    public WebSocketSession getSession() {
        return session;
    }

    public Long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public String getAuthCookie() {
        return authCookie;
    }
}
