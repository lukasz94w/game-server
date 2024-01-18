package pl.lukasz94w.game;

public class GameException extends RuntimeException {

    private String reason;

    public GameException(String reason) {
        super(reason);
    }
}
