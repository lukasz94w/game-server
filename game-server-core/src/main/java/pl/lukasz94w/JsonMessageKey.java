package pl.lukasz94w;

public final class JsonMessageKey {
    private JsonMessageKey() {}

    public final static String SERVER_MESSAGE = "serverMessage";

    public final static String SERVER_HEARTBEAT = "serverHeartbeat";

    public final static String SERVER_PAIRED_SESSION_DISCONNECTED = "serverPairedSessionDisconnected";

    public final static String SERVER_CLIENT_RECEIVED_MESSAGE_CONFIRMATION = "serverClientReceivedMessageConfirmation";

    public final static String SERVER_REJECTION_MESSAGE = "serverRejectionMessage";

    public final static String CLIENT_MESSAGE = "clientMessage";

    public final static String CLIENT_RECEIVED_MESSAGE_CONFIRMATION = "clientReceivedMessageConfirmation";

    public final static String CLIENT_HEARTBEAT = "clientHeartbeat";
}
