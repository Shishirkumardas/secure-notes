package com.securenotes.web;

import com.securenotes.domain.UserAccount;
import com.securenotes.error.ApiException;
import com.securenotes.service.UserService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

  private final UserService users;

  public UserController(UserService users) {
    this.users = users;
  }

  @GetMapping("/api/users/interests")
  public Map<String, Object> interests(
      Authentication authentication, @RequestParam Map<String, String> query) {
    requireAdmin(CurrentUser.get(authentication));
    return users.groupByInterests(query);
  }

  @PutMapping("/api/users/me")
  public Map<String, Object> updateSelf(
      Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
    return users.updateSelf(CurrentUser.get(authentication), body);
  }

  @GetMapping("/api/users")
  public Map<String, Object> list(
      Authentication authentication, @RequestParam Map<String, String> query) {
    requireAdmin(CurrentUser.get(authentication));
    return users.list(query);
  }

  @PostMapping("/api/users")
  public ResponseEntity<Map<String, Object>> create(
      Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
    requireAdmin(CurrentUser.get(authentication));
    return ResponseEntity.status(201).body(users.create(body));
  }

  @GetMapping("/api/users/{id}/posts")
  public Map<String, Object> posts(
      Authentication authentication, @PathVariable String id, @RequestParam Map<String, String> query) {
    CurrentUser.get(authentication);
    return users.postsForUser(id, query);
  }

  @GetMapping("/api/users/{id}")
  public Map<String, Object> get(Authentication authentication, @PathVariable String id) {
    requireAdmin(CurrentUser.get(authentication));
    return users.get(id);
  }

  @PutMapping("/api/users/{id}")
  public Map<String, Object> update(
      Authentication authentication,
      @PathVariable String id,
      @RequestBody(required = false) Map<String, Object> body) {
    return users.update(requireAdmin(CurrentUser.get(authentication)), id, body);
  }

  @DeleteMapping("/api/users/{id}")
  public Map<String, Object> delete(Authentication authentication, @PathVariable String id) {
    return users.delete(requireAdmin(CurrentUser.get(authentication)), id);
  }

  private static UserAccount requireAdmin(UserAccount user) {
    if (user == null || !"admin".equals(user.getRole())) {
      throw new ApiException(403, "Admin access required");
    }
    return user;
  }
}
