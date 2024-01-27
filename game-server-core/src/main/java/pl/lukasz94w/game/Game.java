package pl.lukasz94w.game;

import pl.lukasz94w.player.Player;
import pl.lukasz94w.player.PlayerFactory;
import pl.lukasz94w.tictactoe.Tictactoe;
import pl.lukasz94w.tictactoe.TictactoeFactory;

public class Game {
    private final Player firstPlayer;
    private Player secondPlayer;
    private final Tictactoe tictactoe;

    Game(Player firstPlayer) {
        this.firstPlayer = firstPlayer;
        this.tictactoe = TictactoeFactory.createTictactoe();
        this.secondPlayer = PlayerFactory.createPlayerHolder();
    }

    public void attachSecondPlayer(Player secondPlayer) {
        this.secondPlayer = secondPlayer;
    }

    public Player getFirstPlayer() {
        return firstPlayer;
    }

    public Player getSecondPlayer() {
        return secondPlayer;
    }

    public Tictactoe getTictactoe() {
        return tictactoe;
    }
}
