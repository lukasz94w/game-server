package pl.lukasz94w.exception;

public class GameServerAccessDeniedException extends RuntimeException {

    public GameServerAccessDeniedException(String reason) {
        super(reason);
    }
}
