package pl.lukasz94w.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import pl.lukasz94w.entity.Game;
import pl.lukasz94w.entity.Player;
import pl.lukasz94w.exception.GameException;
import pl.lukasz94w.repository.GameRepository;
import pl.lukasz94w.repository.PlayerRepository;
import pl.lukasz94w.request.FinishedGameData;

import java.util.Date;

@Service
@AllArgsConstructor
public class GameService {

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;

    public void save(FinishedGameData data) {
        // TODO: implement global exception resolver and some logger in here?
        // f.e. logging all exceptions like below ones

        String firstPlayerName = data.getFirstPlayerName();
        String secondPlayerName = data.getSecondPlayerName();
        String winnerName = data.getWinnerName();
        Date gameStarted = data.getGameStarted();
        Date gameEnded = data.getGameEnded();

        // it could also be done by custom ConstraintValidator
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
