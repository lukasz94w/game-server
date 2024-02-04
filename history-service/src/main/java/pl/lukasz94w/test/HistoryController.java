package pl.lukasz94w.test;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/hello")
    public String helloWorld() {
        return "Hello world!";
    }

    @PostMapping("/save")
    public ResponseEntity<Void> save(@RequestBody String name) {
        // TODO: there will be checking whether user is found if not exception
        historyService.save(name);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @GetMapping("/findByName")
    public ResponseEntity<HistoryEntity> findByName(@RequestParam String name) {
        return new ResponseEntity<>(historyService.findByName(name), HttpStatus.OK);
    }
}
