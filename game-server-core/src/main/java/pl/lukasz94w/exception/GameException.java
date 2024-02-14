package pl.lukasz94w.exception;

public class GameException extends RuntimeException {

    public GameException(String reason) {
        super(reason);
    }
}
