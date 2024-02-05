package pl.lukasz94w.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import pl.lukasz94w.entity.Game;
import pl.lukasz94w.entity.Player;
import pl.lukasz94w.exception.GameException;
import pl.lukasz94w.repository.GameRepository;
import pl.lukasz94w.repository.PlayerRepository;
import pl.lukasz94w.request.FinishedGameData;
import pl.lukasz94w.response.GameDto;
import pl.lukasz94w.response.MapperDto;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class GameService {

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final MapperDto mapperDto;

    public void save(FinishedGameData data) {
        String firstPlayerName = data.getFirstPlayerName();
        String secondPlayerName = data.getSecondPlayerName();
        String winnerName = data.getWinnerName();
        Date gameStarted = data.getGameStarted();
        Date gameEnded = data.getGameEnded();

        // it could also be done by custom ConstraintValidator (on pre-processing level of the incoming request)
        validateNamesUniqueness(firstPlayerName, secondPlayerName);
        validateWinnerName(winnerName, firstPlayerName, secondPlayerName);
        validateGameDates(gameStarted, gameEnded);

        Player firstPlayer = playerRepository.findByName(firstPlayerName)
                .orElseThrow(() -> new GameException("First player with name: " + firstPlayerName + ", not found"));

        Player secondPlayer = playerRepository.findByName(data.getSecondPlayerName())
                .orElseThrow(() -> new GameException("Second player with name: " + secondPlayerName + ", not found"));

        Player winner = winnerName.equals(firstPlayerName) ? firstPlayer : secondPlayer;

        Game game = new Game(firstPlayer, secondPlayer, winner, gameStarted, gameEnded, data.getNumberOfWinningMovements());
        gameRepository.save(game);
    }

    public Collection<GameDto> findGamesByUserName(String playerName) {
        Player player = playerRepository.getPlayerByName(playerName);
        if (player == null) {
            return Collections.emptyList();
        }

        Collection<Game> games = gameRepository.findGamesByFirstPlayerOrSecondPlayer(player, player);

        return games.stream()
                .map(mapperDto::mapToGameDto)
                .collect(Collectors.toList());
    }

    private void validateWinnerName(String winnerName, String firstPlayerName, String secondPlayerName) {
        if (!winnerName.equals(firstPlayerName) && !winnerName.equals(secondPlayerName)) {
            throw new GameException("Winner name is not equal to one of the players");
        }
    }

    private void validateNamesUniqueness(String firstPlayerName, String secondPlayerName) {
        if (firstPlayerName.equals(secondPlayerName)) {
            throw new GameException("Players needs to have a different name");
        }
    }

    private void validateGameDates(Date gameStarted, Date gameEnded) {
        if (gameStarted.after(gameEnded)) {
            throw new GameException("Game started date is later than ending time");
        }
    }
}
