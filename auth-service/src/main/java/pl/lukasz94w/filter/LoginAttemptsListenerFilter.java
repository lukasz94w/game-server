package pl.lukasz94w.filter;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import java.util.Base64;
import java.util.UUID;

public class LoginAttemptsListenerFilter extends BasicAuthenticationFilter {

    private final Logger logger = LoggerFactory.getLogger(LoginAttemptsListenerFilter.class);

    private final String cookieName;

    private final Integer cookieMaxAge;

    public LoginAttemptsListenerFilter(AuthenticationManager authenticationManager, String cookieName, Integer cookieMaxAge) {
        super(authenticationManager);
        this.cookieName = cookieName;
        this.cookieMaxAge = cookieMaxAge;
    }

    @Override
    protected void onSuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                              Authentication authResult) {

        logger.info("Successful authentication, user: " + decodeUsernameFromBasicAuth(request.getHeader("Authorization")));

        Cookie safeMirroredSessionCookie = new Cookie(cookieName, UUID.randomUUID().toString());
        safeMirroredSessionCookie.setMaxAge(cookieMaxAge);
        safeMirroredSessionCookie.setPath("/");
        response.addCookie(safeMirroredSessionCookie);
    }


    protected void onUnsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                                AuthenticationException failed) {
        logger.info("Failed authentication, user: " + decodeUsernameFromBasicAuth(request.getHeader("Authorization")));
    }

    private String decodeUsernameFromBasicAuth(String basicAuthData) {
        if (basicAuthData == null || basicAuthData.isEmpty()) {
            return null;
        }

        String encodedCredentials = basicAuthData.replaceFirst("Basic ", "");
        byte[] decodedBytes = Base64.getDecoder().decode(encodedCredentials);
        String decodedCredentials = new String(decodedBytes);

        String[] credentialsArray = decodedCredentials.split(":");
        if (credentialsArray.length > 0) {
            return credentialsArray[0];
        } else {
            return null;
        }
    }
}
