package pl.lukasz94w.exception;

public class GameException extends RuntimeException {

    private String reason;

    public GameException(String reason) {
        super(reason);
    }
}
