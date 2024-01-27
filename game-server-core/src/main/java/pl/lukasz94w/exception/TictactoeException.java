package pl.lukasz94w.exception;

public class TictactoeException extends RuntimeException {

    private String reason;

    public TictactoeException(String reason) {
        super(reason);
    }
}
