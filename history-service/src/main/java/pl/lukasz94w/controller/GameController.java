package pl.lukasz94w.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.lukasz94w.request.FinishedGameData;
import pl.lukasz94w.response.GameDto;
import pl.lukasz94w.service.GameService;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.Collection;

@RestController
@RequestMapping("api/v1/game")
@AllArgsConstructor
public class GameController {

    private final GameService gameService;

    @PostMapping("/save")
    public ResponseEntity<Void> save(@Valid @RequestBody FinishedGameData data) {
        gameService.save(data);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @GetMapping("/findGames/{playerName}")
    public ResponseEntity<Collection<GameDto>> findGamesByUserName(@PathVariable @NotBlank @Size(max = 100) String playerName) {
        return new ResponseEntity<>(gameService.findGamesByUserName(playerName), HttpStatus.OK);
    }
}
