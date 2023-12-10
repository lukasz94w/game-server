package pl.lukasz94w;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    @GetMapping("/sign-in")
    public String login() {
        return "Successfully logged in!";
    }
}
