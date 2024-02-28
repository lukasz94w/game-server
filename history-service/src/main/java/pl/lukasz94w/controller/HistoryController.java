package pl.lukasz94w.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.lukasz94w.request.FinishedGameData;
import pl.lukasz94w.response.GameDto;
import pl.lukasz94w.service.GameService;

import javax.validation.Valid;
import java.util.Collection;

@RestController
@RequestMapping("api/v1/history")
@AllArgsConstructor
public class HistoryController {

    private final GameService gameService;

    @PostMapping("/save")
    public ResponseEntity<Void> save(@Valid @RequestBody FinishedGameData data) {
        gameService.save(data);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @GetMapping("/findGamesForUser")
    public ResponseEntity<Collection<GameDto>> findGamesForUser(@RequestHeader HttpHeaders requestHeaders) {
        String userName = requestHeaders.getFirst("userName"); // coming from api-gateway-service
        return new ResponseEntity<>(gameService.findGamesForUser(userName), HttpStatus.OK);
    }
}
