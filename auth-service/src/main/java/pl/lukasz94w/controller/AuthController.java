package pl.lukasz94w.controller;

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
        return successData(authentication);
    }

    @GetMapping("/verifyCookieAndGetUserName")
    public ResponseEntity<String> verifyCookieAndGetUserName(Authentication authentication) {
        return successData(authentication);
    }

    @GetMapping("/refreshSession")
    public ResponseEntity<String> refreshSession(Authentication authentication) {
        return successData(authentication);
    }

    private ResponseEntity<String> successData(Authentication authentication) {
        return new ResponseEntity<>(authentication.getName(), HttpStatus.OK);
    }
}
