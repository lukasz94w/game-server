package pl.lukasz94w.entity;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Getter
@NoArgsConstructor(force = true)
@RequiredArgsConstructor
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "firstPlayerId")
    @NonNull
    private final Player firstPlayer;

    @ManyToOne
    @JoinColumn(name = "secondPlayerId")
    @NonNull
    private final Player secondPlayer;

    @ManyToOne
    @JoinColumn(name = "winnerPlayerId")
    @Nullable
    private final Player winnerPlayer;

    @Temporal(TemporalType.TIMESTAMP)
    @NonNull
    private final ZonedDateTime gameStartedUTC;

    @Temporal(TemporalType.TIMESTAMP)
    @NonNull
    private final ZonedDateTime gameEndedUTC;

    @NonNull
    private final Integer numberOfWinningMovements;
}
