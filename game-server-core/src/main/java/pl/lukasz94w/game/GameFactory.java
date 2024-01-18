package pl.lukasz94w.game;

public class GameFactory {

    private GameFactory() {}

    public static Game getInstance() {
        return new Game();
    }
}
