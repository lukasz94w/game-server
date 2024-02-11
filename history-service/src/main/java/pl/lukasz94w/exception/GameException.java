package pl.lukasz94w.exception;

import lombok.Getter;

@Getter
public class GameException extends RuntimeException {

    private String reason;

    public GameException(String reason) {
        super(reason);
    }
}
