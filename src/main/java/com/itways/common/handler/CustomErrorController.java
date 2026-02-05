package com.itways.common.handler;

import com.itways.common.response.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Custom Error Controller to handle errors that occur outside the Spring MVC
 * dispatching
 * (e.g., 404s for non-existent paths, errors in filters).
 */
@RestController
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ApiResponse<Object>> handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());
            httpStatus = HttpStatus.resolve(statusCode);
            if (httpStatus == null)
                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        String errorMessage = (message != null && !message.toString().isEmpty())
                ? message.toString()
                : "An unexpected error occurred at the framework level";

        if (exception instanceof Throwable) {
            errorMessage = ((Throwable) exception).getMessage();
        } else if (httpStatus == HttpStatus.NOT_FOUND) {
            errorMessage = "The requested resource was not found";
        }

        return ResponseEntity.status(httpStatus)
                .body(ApiResponse.error(errorMessage, "FRAMEWORK_ERROR_" + httpStatus.value()));
    }
}
