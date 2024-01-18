package pl.lukasz94w.game;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class Game {
    private final Map<Integer, String> boardState;

    Game() {
        boardState = new HashMap<>();
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
            throw new GameException("Square number must be a value between 0 or 8");
        }
    }

    private void validateSquareValue(String squareValue) {
        if (!squareValue.equals("X") && !squareValue.equals("O")) {
            throw new GameException("Wrong square value. Accepted: X or O");
        }
    }

    private void validateOccupancy(Integer squareNumber) {
        if (boardState.containsKey(squareNumber)) {
            String value = boardState.get(squareNumber);
            throw new GameException("Square already marked by: " + value);
        }
    }

    public State determineGameState() {
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

        // Check for a winner
        for (int[] combination : winningCombinations) {
            int a = combination[0];
            int b = combination[1];
            int c = combination[2];

            if (boardState.containsKey(a) && boardState.containsKey(b) && boardState.containsKey(c)) {
                String valueA = boardState.get(a);
                String valueB = boardState.get(b);
                String valueC = boardState.get(c);

                if (valueA.equals(valueB) && valueA.equals(valueC)) {
                    if (valueA.equals("X")) {
                        return State.FIRST_PLAYER_X_WON;
                    } else {
                        return State.SECOND_PLAYER_O_WON;
                    }
                }
            }
        }

        if (boardState.size() == 9) {
            return State.DRAW;
        }

        return State.UNRESOLVED;
    }

    public enum State {
        UNRESOLVED(""),
        FIRST_PLAYER_X_WON("1st player won"),
        SECOND_PLAYER_O_WON("2nd player won"),
        DRAW("Draw");

        private final String message;

        State(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    @Override
    public String toString() {
        return "New game, started point at: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"));
    }
}
