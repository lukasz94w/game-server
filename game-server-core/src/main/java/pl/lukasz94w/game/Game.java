package pl.lukasz94w.game;

import lombok.Getter;
import pl.lukasz94w.player.Player;
import pl.lukasz94w.tictactoe.Tictactoe;
import pl.lukasz94w.tictactoe.TictactoeFactory;

@Getter
public class Game {
    private final Player firstPlayer;
    private final Player secondPlayer;
    private final Tictactoe tictactoe;

    Game(Player firstPlayer, Player secondPlayer) {
        this.firstPlayer = firstPlayer;
        this.secondPlayer = secondPlayer;
        tictactoe = TictactoeFactory.createTictactoe();
    }
}
