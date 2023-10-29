package pl.lukasz94w;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestEndpoint {

    @GetMapping(value = "/test")
    public String test() {
        return "Hello world";
    }
}
