package pl.lukasz94w.request;

import lombok.Getter;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.*;
import java.util.Date;

@Getter // TODO: needed during json generation?
public class FinishedGameData {

    @NotBlank
    @Size(max = 100)
    private String firstPlayerName;

    @NotBlank
    @Size(max = 100)
    private String secondPlayerName;

    @NotBlank
    @Size(max = 100)
    private String winnerName;

    @Past
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date gameStarted;

    @Past
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date gameEnded;

    @Positive
    @Max(value = 5)
    private Integer numberOfWinningMovements;
}
