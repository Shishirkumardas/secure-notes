package com.securenotes.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JsonErrors {

  private final ObjectMapper objectMapper;

  public JsonErrors(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void write(HttpServletResponse response, int status, String message) throws IOException {
    if (response.isCommitted()) {
      return;
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("error", message);
    response.setStatus(status);
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json");
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }
}
