package com.securenotes.web;

import com.securenotes.mongo.DatabaseLifecycle;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  private final DatabaseLifecycle database;

  public HealthController(DatabaseLifecycle database) {
    this.database = database;
  }

  @GetMapping("/api/health")
  public ResponseEntity<Map<String, Object>> health() {
    boolean connected = database.isConnected();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("ok", connected);
    body.put("db", connected ? "connected" : "disconnected");
    return ResponseEntity.ok(body);
  }

  @GetMapping("/")
  public Map<String, String> root() {
    return Map.of("service", "secure-notes-api", "health", "/api/health");
  }
}
