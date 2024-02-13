package pl.lukasz94w.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.ZonedDateTime;

@AllArgsConstructor
@Getter
public class GameDto {

    private String firstPlayerName;

    private String secondPlayerName;

    private String winnerPlayerName;

    private ZonedDateTime gameStarted;

    private ZonedDateTime gameEnded;

    private Integer numberOfWinningMovements;
}
