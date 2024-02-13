package pl.lukasz94w.response;

import org.springframework.stereotype.Component;
import pl.lukasz94w.entity.Game;
import pl.lukasz94w.entity.Player;

@Component
public class MapperDto {

    public GameDto mapToGameDto(Game game) {
        return new GameDto(game.getFirstPlayer().getName(), game.getSecondPlayer().getName(), getWinnerPlayerName(game.getWinnerPlayer()),
                game.getGameStartedUTC(), game.getGameEndedUTC(), game.getNumberOfWinningMovements());
    }

    private String getWinnerPlayerName(Player winner) {
        return winner == null ? "" : winner.getName();
    }
}
