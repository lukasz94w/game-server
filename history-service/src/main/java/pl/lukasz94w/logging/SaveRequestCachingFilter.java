package pl.lukasz94w.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Order(value = Ordered.HIGHEST_PRECEDENCE)
@Component
@WebFilter(filterName = "SaveRequestCachingFilter", urlPatterns = "/save")
public class SaveRequestCachingFilter extends OncePerRequestFilter {

    private final static Logger logger = LoggerFactory.getLogger(SaveRequestCachingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CachedHttpServletRequest cachedHttpServletRequest = new CachedHttpServletRequest(request);
        logger.info("Request body: " + IOUtils.toString(cachedHttpServletRequest.getInputStream(), StandardCharsets.UTF_8));
        filterChain.doFilter(cachedHttpServletRequest, response);
    }
}
