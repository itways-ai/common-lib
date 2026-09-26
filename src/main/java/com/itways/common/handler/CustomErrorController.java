package com.itways.common.handler;

import com.itways.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Custom Error Controller to handle errors that occur outside the Spring MVC
 * dispatching (e.g., 404s for non-existent paths, errors in filters,
 * {@code sendError} from Spring Security).
 *
 * <p>It never echoes the exception or container message (AS-13): those carried
 * class names, SQL and stack details to the client. A 5xx gets a fixed text and
 * the exception goes to the log; any other status gets its reason phrase.
 */
@Slf4j
@RestController
@Hidden
public class CustomErrorController implements ErrorController {

    @Hidden
    @RequestMapping("/error")
    public ResponseEntity<ApiResponse<Object>> handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        if (status != null) {
            try {
                HttpStatus resolved = HttpStatus.resolve(Integer.parseInt(status.toString()));
                if (resolved != null) {
                    httpStatus = resolved;
                }
            } catch (NumberFormatException ignored) {
                // keep 500
            }
        }

        String errorMessage;
        String errorCode;
        if (httpStatus == HttpStatus.NOT_FOUND) {
            errorMessage = "The requested resource was not found";
            errorCode = "NOT_FOUND";
        } else if (httpStatus.is5xxServerError()) {
            Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
            if (exception instanceof Throwable throwable) {
                log.error("Request {} failed with status {}", uri, httpStatus.value(), throwable);
            } else {
                log.error("Request {} failed with status {}: {}", uri, httpStatus.value(),
                        request.getAttribute(RequestDispatcher.ERROR_MESSAGE));
            }
            errorMessage = "Internal server error";
            errorCode = "INTERNAL_SERVER_ERROR";
        } else {
            errorMessage = httpStatus.getReasonPhrase();
            errorCode = "FRAMEWORK_ERROR_" + httpStatus.value();
        }

        return ResponseEntity.status(httpStatus).body(ApiResponse.error(errorMessage, errorCode));
    }
}
