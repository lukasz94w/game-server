package pl.lukasz94w.game;

import pl.lukasz94w.player.Player;

public class GameFactory {

    private GameFactory() {
    }

    public static Game createGame(Player firstPlayer) {
        return new Game(firstPlayer);
    }
}
