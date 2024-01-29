package pl.lukasz94w;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/auth")
public class AuthController {

    // basically it should be POST request...
    @GetMapping("/signIn")
    public String signIn() {
        return "Successfully logged in!";
    }

    @GetMapping("/verifySessionActive")
    public String verifySessionActive(HttpSession httpSession) {
        return "Valid user (active session id: " + httpSession.getId() + ")";
    }
}
