package pl.lukasz94w.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.filter.AbstractRequestLoggingFilter;

public class WithoutLoggingBeforeRequestFilter extends AbstractRequestLoggingFilter {

    @Override
    protected void beforeRequest(HttpServletRequest httpServletRequest, String message) {
        // avoid log beforeRequest (containing unprocessed data)
    }

    @Override
    protected void afterRequest(HttpServletRequest httpServletRequest, String message) {
        logger.debug(message);
    }
}