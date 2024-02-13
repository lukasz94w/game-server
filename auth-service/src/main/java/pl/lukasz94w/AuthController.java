package pl.lukasz94w;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/auth")
public class AuthController {

    // basically it should be POST request...
    @GetMapping("/signIn")
    public ResponseEntity<String> signIn(Authentication authentication) {
        return new ResponseEntity<>(authentication.getName(), HttpStatus.OK);
    }

    @GetMapping("/getUserName")
    public ResponseEntity<String> getUserName(Authentication authentication) {
        return new ResponseEntity<>(authentication.getName(), HttpStatus.OK);
    }

    @GetMapping("/verifySessionActive")
    public ResponseEntity<String> verifySessionActive(Authentication authentication, HttpSession httpSession) {
        String responseBody = authentication.getName() + " successfully authenticated. Session id: " + httpSession.getId();
        return new ResponseEntity<>(responseBody, HttpStatus.OK);
    }
}
