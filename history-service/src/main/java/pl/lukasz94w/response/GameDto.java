package pl.lukasz94w.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Date;

@AllArgsConstructor
@Getter
public class GameDto {

    private String firstPlayerName;

    private String secondPlayerName;

    private String winnerPlayerName;

    private Date gameStarted;

    private Date gameEnded;

    private Integer numberOfWinningMovements;
}
