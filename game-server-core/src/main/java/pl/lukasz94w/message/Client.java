package pl.lukasz94w.message;

public class Client {

    private Client() {
    }

    public final static String CLIENT_MESSAGE_NEW_MESSAGE = "clientMessageNewMessage";

    public final static String CLIENT_MESSAGE_RECEIVED_MESSAGE_CONFIRMATION = "clientMessageReceivedMessageConfirmation";

    public final static String CLIENT_MESSAGE_RECEIVED_GAME_STATUS_UPDATE_CONFIRMATION = "clientMessageReceivedGameStatusUpdateConfirmation";

    public final static String CLIENT_GAME_UPDATE_CHOSEN_SQUARE_NUMBER = "clientGameUpdateChosenSquareNumber";

    public final static String CLIENT_GAME_UPDATE_CHOSEN_SQUARE_VALUE = "clientGameUpdateChosenSquareValue";

    public final static String CLIENT_SESSION_STATUS_UPDATE_HEARTBEAT = "clientSessionStatusUpdateHeartbeat";
}
