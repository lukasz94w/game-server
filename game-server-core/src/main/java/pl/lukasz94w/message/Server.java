package pl.lukasz94w.message;

public class Server {

    private Server() {
    }

    public final static String SERVER_MESSAGE_OPPONENT_MESSAGE = "serverMessageOpponentMessage";

    public final static String SERVER_GAME_UPDATE_GAME_STARTED = "serverGameUpdateGameStarted";

    public final static String SERVER_GAME_UPDATE_GAME_CHANGED = "serverGameUpdateGameChanged";

    public final static String SERVER_GAME_OPPONENT_RECEIVED_GAME_STATUS_CHANGE_CONFIRMATION = "serverGameOpponentReceivedGameStatusChangeConfirmation";

    public final static String SERVER_GAME_UPDATE_GAME_ENDED = "serverGameUpdateGameEnded";

    public final static String SERVER_SESSION_STATUS_UPDATE_HEARTBEAT = "serverSessionStatusUpdateHeartbeat";

    public final static String SERVER_SESSION_STATUS_UPDATE_PAIRED_SESSION_DISCONNECTED = "serverSessionStatusUpdatePairedSessionDisconnected";

    public final static String SERVER_SESSION_STATUS_UPDATE_SESSION_REJECTED = "serverSessionStatusUpdateSessionRejected";
}
