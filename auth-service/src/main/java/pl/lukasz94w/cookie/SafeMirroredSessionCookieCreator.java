package pl.lukasz94w.cookie;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class SafeMirroredSessionCookieCreator extends OncePerRequestFilter {

    private final String cookieName;

    private final Integer cookieMaxAge;

    public SafeMirroredSessionCookieCreator(String cookieName, Integer cookieMaxAge) {
        this.cookieName = cookieName;
        this.cookieMaxAge = cookieMaxAge;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Cookie customCookie = new Cookie(cookieName, UUID.randomUUID().toString());
        customCookie.setMaxAge(cookieMaxAge);
        customCookie.setPath("/");
        response.addCookie(customCookie);

        filterChain.doFilter(request, response);
    }
}
