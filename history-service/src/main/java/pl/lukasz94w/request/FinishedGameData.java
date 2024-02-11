package pl.lukasz94w.request;

import lombok.Getter;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.*;
import java.time.ZonedDateTime;

@Getter
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
    private ZonedDateTime gameStartedUTC;

    @Past
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime gameEndedUTC;

    @Positive
    @Max(value = 5)
    private Integer numberOfWinningMovements;
}
