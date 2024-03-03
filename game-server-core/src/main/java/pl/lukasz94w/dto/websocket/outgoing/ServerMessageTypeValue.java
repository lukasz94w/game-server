package pl.lukasz94w.dto.websocket.outgoing;

public class ServerMessageTypeValue {

    private ServerMessageTypeValue() {
    }

    public final static String OPPONENT_MESSAGE = "opponentMessage";

    public final static String GAME_STARTED = "gameStarted";

    public final static String GAME_UPDATED = "gameUpdated";

    public final static String OPPONENT_RECEIVED_GAME_UPDATE_CONFIRMATION = "opponentReceivedGameUpdateConfirmation";

    public final static String GAME_ENDED = "gameEnded";

    public final static String HEARTBEAT_RECEIVED_CONFIRMATION = "heartbeatReceivedConfirmation";

    public final static String PAIRED_SESSION_DISCONNECTED = "pairedSessionDisconnected";

    public final static String SESSION_REJECTED = "sessionRejected";
}
