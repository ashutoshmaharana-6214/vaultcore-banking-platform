package com.ashutosh.banking.exception;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log =
        LoggerFactory.getLogger(ApiExceptionHandler.class);
 @ExceptionHandler(ApiException.class) ResponseEntity<?> handle(ApiException e){return ResponseEntity.status(e.getStatus()).body(Map.of("timestamp",Instant.now(),"error",e.getMessage()));}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException e){return ResponseEntity.badRequest().body(Map.of("timestamp",Instant.now(),"error","Validation failed","details",e.getBindingResult().getFieldErrors().stream().map(x->x.getField()+": "+x.getDefaultMessage()).toList()));}
@ExceptionHandler(Exception.class)
ResponseEntity<?> other(Exception e) {
    log.error("Unexpected API error", e);

    return ResponseEntity.internalServerError()
            .body(Map.of(
                    "timestamp", Instant.now(),
                    "error", "Unexpected server error"
            ));
}

@ExceptionHandler(
    org.springframework.web.bind.MissingRequestHeaderException.class
)
ResponseEntity<?> missingHeader(
        org.springframework.web.bind.MissingRequestHeaderException e) {

    return ResponseEntity.badRequest().body(Map.of(
            "timestamp", Instant.now(),
            "error", "Missing required header: " + e.getHeaderName()
    ));
}

}
