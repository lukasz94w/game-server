package pl.lukasz94w;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/auth")
public class AuthController {

    @GetMapping("/sign-in")
    public String signIn() {
        return "Successfully logged in!";
    }

    @GetMapping("/verify")
    public String verifySignedIn(HttpSession httpSession) {
        return "Valid user (active session id: " + httpSession.getId() + ")";
    }
}
