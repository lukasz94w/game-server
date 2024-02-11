package pl.lukasz94w.exception;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(GameException.class)
    public ResponseEntity<String> handleGameException(GameException gameException) {
        logger.error("Game exception occurred, reason: " + gameException.getMessage());
        return new ResponseEntity<>(gameException.getMessage(), HttpStatus.PRECONDITION_FAILED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> defaultExceptionHandler(Exception exception) {
        logger.error("Exception occurred, stacktrace: " + ExceptionUtils.getStackTrace(exception));
        return new ResponseEntity<>(ExceptionUtils.getStackTrace(exception), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
