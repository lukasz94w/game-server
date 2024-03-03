package pl.lukasz94w.dto.http.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.ZonedDateTime;

@AllArgsConstructor
@Getter
public class FinishedGameData {

    private String firstPlayerName;

    private String secondPlayerName;

    private String winnerName;

    private ZonedDateTime gameStartedUTC;

    private ZonedDateTime gameEndedUTC;

    private Integer numberOfWinningMovements;
}
