package com.securenotes.web;

import com.securenotes.service.AuthService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/api/auth/register")
  public ResponseEntity<Map<String, Object>> register(@RequestBody(required = false) Map<String, Object> body) {
    return ResponseEntity.status(201).body(auth.register(body));
  }

  @PostMapping("/api/auth/login")
  public Map<String, Object> login(@RequestBody(required = false) Map<String, Object> body) {
    return auth.login(body);
  }

  @GetMapping("/api/auth/me")
  public Map<String, Object> me(Authentication authentication) {
    return auth.me(CurrentUser.get(authentication));
  }
}
