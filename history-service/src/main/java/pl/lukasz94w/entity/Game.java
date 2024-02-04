package pl.lukasz94w.entity;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Date;

@Entity
@NoArgsConstructor
@RequiredArgsConstructor
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "firstPlayerId")
    @NonNull
    private Player firstPlayer;

    @ManyToOne
    @JoinColumn(name = "secondPlayerId")
    @NonNull
    private Player secondPlayer;

    @ManyToOne
    @JoinColumn(name = "winnerPlayerId")
    @NonNull
    private Player winnerPlayer;

    @Temporal(TemporalType.TIMESTAMP)
    @NonNull
    private Date gameStarted;

    @Temporal(TemporalType.TIMESTAMP)
    @NonNull
    private Date gameEnded;

    @NonNull
    private Integer numberOfWinMovements;
}
