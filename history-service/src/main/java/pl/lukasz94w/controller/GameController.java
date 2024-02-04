package pl.lukasz94w.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.lukasz94w.request.FinishedGameData;
import pl.lukasz94w.service.GameService;

import javax.validation.Valid;

@RestController
@RequestMapping("api/v1/game")
@AllArgsConstructor
public class GameController {

    private final GameService gameService;

    @PostMapping("/save")
    public ResponseEntity<Void> save(@Valid @RequestBody FinishedGameData data) {

        // there will be checking whether player exists and create if now,
        // write comment that there should be players created (when auth service is created)
        // or i could create them using data.sql
        // instead there is solution like that
        // TODO: implement registration in authService?

//        // TODO: there will be checking whether user is found if not exception
//
        gameService.save(data);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }
}
