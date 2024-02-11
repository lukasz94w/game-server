package pl.lukasz94w.response;

import org.springframework.stereotype.Component;
import pl.lukasz94w.entity.Game;

@Component
public class MapperDto {

    public GameDto mapToGameDto(Game game) {
        return new GameDto(game.getFirstPlayer().getName(), game.getSecondPlayer().getName(), game.getWinnerPlayer().getName(),
                game.getGameStartedUTC(), game.getGameEndedUTC(), game.getNumberOfWinningMovements());
    }
}
