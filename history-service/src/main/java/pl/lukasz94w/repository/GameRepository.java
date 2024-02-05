package pl.lukasz94w.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.lukasz94w.entity.Game;
import pl.lukasz94w.entity.Player;

import java.util.Collection;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {

    Collection<Game> findGamesByFirstPlayerOrSecondPlayer(Player firstPlayer, Player secondPlayer);
}
