package pl.lukasz94w.tictactoe;

public class TictactoeFactory {

    private TictactoeFactory() {
    }

    public static Tictactoe createTictactoe() {
        return new Tictactoe();
    }
}
