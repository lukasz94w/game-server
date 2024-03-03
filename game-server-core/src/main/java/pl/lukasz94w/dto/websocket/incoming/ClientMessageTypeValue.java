package pl.lukasz94w.dto.websocket.incoming;

public class ClientMessageTypeValue {

    private ClientMessageTypeValue() {
    }

    public final static String PLAYER_MESSAGE = "playerMessage";

    public final static String GAME_UPDATE = "gameUpdate";

    public final static String HEARTBEAT = "heartbeat";

    public final static String PLAYER_RECEIVED_GAME_UPDATE_CONFIRMATION = "playerReceivedGameUpdateConfirmation";
}
