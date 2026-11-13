package com.securenotes.error;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<Map<String, String>> api(ApiException ex) {
    return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException ex) {
    String message = mismatched(ex)
        ? "Request body must be a JSON object"
        : "Request body must be valid JSON";
    return ResponseEntity.badRequest().body(Map.of("error", message));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, String>> duplicate(DataIntegrityViolationException ex) {
    return ResponseEntity.status(409).body(Map.of("error", "Email is already in use"));
  }

  @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
  public ResponseEntity<Map<String, String>> missing() {
    return ResponseEntity.status(404).body(Map.of("error", "Not found"));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<Map<String, String>> method() {
    return ResponseEntity.status(404).body(Map.of("error", "Not found"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> unexpected(Exception ex) {
    if (ex instanceof ApiException api) {
      return api(api);
    }
    org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class).error("Request failed", ex);
    return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
  }

  private static boolean mismatched(Throwable ex) {
    Throwable current = ex;
    while (current != null) {
      if (current instanceof MismatchedInputException) {
        return true;
      }
      String message = current.getMessage();
      if (message != null && message.contains("JsonToken.START_ARRAY")) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
