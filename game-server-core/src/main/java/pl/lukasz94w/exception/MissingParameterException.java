package pl.lukasz94w.exception;

public class MissingParameterException extends RuntimeException {

    public MissingParameterException(String message) {
        super(message);
    }
}
