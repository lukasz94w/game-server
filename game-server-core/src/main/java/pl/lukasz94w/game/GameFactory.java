package pl.lukasz94w.game;

public class GameFactory {

    private GameFactory() {
    }

    public static Game createNewGame() {
        return new Game();
    }
}
