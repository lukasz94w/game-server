package pl.lukasz94w.logout;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;

public class ClearDataLogoutHandler implements LogoutHandler {

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        // These headers need to be set because if not browser is complaining (CORS)
        // when it gets response after reaching logout endpoint (its like similar to
        // corsFilter()) set for secured endpoints.
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:3000");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        // response.setStatus(response.SC_NO_CONTENT);

        // In case there are custom cookies set during creating a session we need to clear them (told the browser)
        // they are not valid anymore by setting maxAge to 0. After getting such response browser will automatically
        // remove such cookies and won't use them in subsequent requests.
        // for (Cookie cookie : request.getCookies()) {
        //      String cookieName = cookie.getName();
        //      Cookie cookieToDelete = new Cookie(cookieName, null);
        //      cookieToDelete.setMaxAge(0);
        //      response.addCookie(cookieToDelete);
        //  }
        // Another way of telling the browser to get rid of the cookies is to use following code which set one
        // header telling the browser explicitly to remove ALL cookies and other session leftovers like cache etc.
        // ClearSiteDataHeaderWriter.Directive[] SOURCE = {CACHE, COOKIES, STORAGE, EXECUTION_CONTEXTS};
        // HeaderWriterLogoutHandler headerWriterLogoutHandler = new HeaderWriterLogoutHandler(new ClearSiteDataHeaderWriter(SOURCE));

        // There are other things which needs to be cleared during session logout, but it's provided by Spring itself,
        // like invalidate session and so on. Read more here:
        // https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html#logout-java-configuration
    }
}
