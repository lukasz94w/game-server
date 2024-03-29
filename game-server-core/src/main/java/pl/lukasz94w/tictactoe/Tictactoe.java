package pl.lukasz94w.tictactoe;

import lombok.Getter;
import pl.lukasz94w.exception.TictactoeException;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

public class Tictactoe {

    private static int totalTictactoeNumber;

    private static final String FIRST_PLAYER_SYMBOL = "X";

    private static final String SECOND_PLAYER_SYMBOL = "O";

    private final Map<Integer, String> boardState;

    @Getter
    private final ZonedDateTime gameStartedUTC;

    @Getter
    private ZonedDateTime gameEndedUTC;

    @Getter
    private int numberOfWinningMovements;

    Tictactoe() {
        boardState = new HashMap<>();
        gameStartedUTC = getCurrentUTCZonedDateTime();
        totalTictactoeNumber++;
        numberOfWinningMovements = 0;
    }

    // there could be also implemented features like tracking the actual player turn
    // and throwing exception when there is a try of changing board state by unauthorized player
    public void updateState(String squareNumberAsString, String squareValue) {
        Integer squareNumber = Integer.parseInt(squareNumberAsString);
        validateSquareNumber(squareNumber);
        validateSquareValue(squareValue);
        validateOccupancy(squareNumber);
        boardState.put(squareNumber, squareValue);
    }

    private void validateSquareNumber(Integer squareNumber) {
        if (squareNumber < 0 || squareNumber > 8) {
            throw new TictactoeException("Square number must be a value between 0 or 8");
        }
    }

    private void validateSquareValue(String squareValue) {
        if (!squareValue.equals(FIRST_PLAYER_SYMBOL) && !squareValue.equals(SECOND_PLAYER_SYMBOL)) {
            throw new TictactoeException("Wrong square value. Accepted: " + FIRST_PLAYER_SYMBOL + " or " + SECOND_PLAYER_SYMBOL);
        }
    }

    private void validateOccupancy(Integer squareNumber) {
        if (boardState.containsKey(squareNumber)) {
            String value = boardState.get(squareNumber);
            throw new TictactoeException("Square already marked by: " + value);
        }
    }

    public Result checkWhetherGameEnded() {
        int[][] winningCombinations = {
                {0, 1, 2},
                {3, 4, 5},
                {6, 7, 8},
                {0, 3, 6},
                {1, 4, 7},
                {2, 5, 8},
                {0, 4, 8},
                {2, 4, 6}
        };

        for (int[] combination : winningCombinations) {
            int a = combination[0];
            int b = combination[1];
            int c = combination[2];

            if (boardState.containsKey(a) && boardState.containsKey(b) && boardState.containsKey(c)) {
                String valueA = boardState.get(a);
                String valueB = boardState.get(b);
                String valueC = boardState.get(c);

                if (valueA.equals(valueB) && valueA.equals(valueC)) {
                    if (valueA.equals(FIRST_PLAYER_SYMBOL)) {
                        numberOfWinningMovements = getNumberOfWinningMovements(FIRST_PLAYER_SYMBOL);
                        gameEndedUTC = getCurrentUTCZonedDateTime();
                        return Result.FIRST_PLAYER_WON;
                    } else {
                        numberOfWinningMovements = getNumberOfWinningMovements(SECOND_PLAYER_SYMBOL);
                        gameEndedUTC = getCurrentUTCZonedDateTime();
                        return Result.SECOND_PLAYER_WON;
                    }
                }
            }
        }

        if (boardState.size() == 9) {
            gameEndedUTC = getCurrentUTCZonedDateTime();
            return Result.UNRESOLVED;
        }

        return Result.ONGOING;
    }

    private ZonedDateTime getCurrentUTCZonedDateTime() {
        return ZonedDateTime.now(ZoneId.of("Europe/London"));
    }

    private int getNumberOfWinningMovements(String winnerSymbol) {
        return (int) boardState.values()
                .stream()
                .filter(symbol -> symbol.equals(winnerSymbol))
                .count();
    }

    public enum Result {
        ONGOING(""),
        FIRST_PLAYER_WON("1st player won"),
        SECOND_PLAYER_WON("2nd player won"),
        UNRESOLVED("Unresolved");

        private final String message;

        Result(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    @Override
    public String toString() {
        return "New game started, total number: " + totalTictactoeNumber;
    }
}
