package pl.lukasz94w.service;

import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;
import pl.lukasz94w.entity.Game;
import pl.lukasz94w.entity.Player;
import pl.lukasz94w.exception.GameException;
import pl.lukasz94w.repository.GameRepository;
import pl.lukasz94w.repository.PlayerRepository;
import pl.lukasz94w.request.FinishedGameData;
import pl.lukasz94w.response.GameDto;
import pl.lukasz94w.response.MapperDto;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.stream.Collectors;

@Service
public class GameService {

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final MapperDto mapperDto;

    public GameService(GameRepository gameRepository, PlayerRepository playerRepository, MapperDto mapperDto) {
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
        this.mapperDto = mapperDto;
    }

    public void save(FinishedGameData data) {
        String firstPlayerName = data.getFirstPlayerName();
        String secondPlayerName = data.getSecondPlayerName();
        String winnerName = data.getWinnerName();
        ZonedDateTime gameStartedUTC = data.getGameStartedUTC();
        ZonedDateTime gameEndedUTC = data.getGameEndedUTC();
        Integer numberOfWinningMovements = data.getNumberOfWinningMovements();

        // it could also be done by custom ConstraintValidator (on pre-processing level of the incoming request)
        validateNamesUniqueness(firstPlayerName, secondPlayerName);
        validateWinnerName(winnerName, firstPlayerName, secondPlayerName);
        validateGameDates(gameStartedUTC, gameEndedUTC);

        Player firstPlayer = playerRepository.findByName(firstPlayerName)
                .orElseThrow(() -> new GameException("First player with name: " + firstPlayerName + ", not found"));

        Player secondPlayer = playerRepository.findByName(data.getSecondPlayerName())
                .orElseThrow(() -> new GameException("Second player with name: " + secondPlayerName + ", not found"));

        Player winner = getWinner(winnerName, firstPlayerName, firstPlayer, secondPlayerName, secondPlayer);

        Game game = new Game(firstPlayer, secondPlayer, winner, gameStartedUTC, gameEndedUTC, numberOfWinningMovements);
        gameRepository.save(game);
    }

    public Collection<GameDto> findGamesForUser(String userName) {
        Player player = playerRepository.getPlayerByName(userName);
        Collection<Game> games = gameRepository.findGamesByFirstPlayerOrSecondPlayer(player, player);

        return games.stream()
                .map(mapperDto::mapToGameDto)
                .collect(Collectors.toList());
    }

    private void validateWinnerName(String winnerName, String firstPlayerName, String secondPlayerName) {
        if (!winnerName.equals(firstPlayerName) && !winnerName.equals(secondPlayerName) && !winnerName.isEmpty()) {
            throw new GameException("Winner name is not equal to one of the players or is not empty (meaning there is no winner)");
        }
    }

    private void validateNamesUniqueness(String firstPlayerName, String secondPlayerName) {
        if (firstPlayerName.equals(secondPlayerName)) {
            throw new GameException("Players needs to have a different name");
        }
    }

    private void validateGameDates(ZonedDateTime gameStarted, ZonedDateTime gameEnded) {
        if (gameStarted.compareTo(gameEnded) > 0) {
            throw new GameException("Game started date is later than ending time");
        }
    }

    @Nullable
    private Player getWinner(String winnerName, String firstPlayerName, Player firstPlayer, String secondPlayerName, Player secondPlayer) {
        if (winnerName.equals(firstPlayerName)) {
            return firstPlayer;
        } else if (winnerName.equals(secondPlayerName)) {
            return secondPlayer;
        } else {
            return null;
        }
    }
}
