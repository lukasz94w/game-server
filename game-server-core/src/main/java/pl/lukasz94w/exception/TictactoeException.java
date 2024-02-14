package pl.lukasz94w.exception;

public class TictactoeException extends RuntimeException {

    public TictactoeException(String reason) {
        super(reason);
    }
}
