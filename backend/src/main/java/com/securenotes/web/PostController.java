package com.securenotes.web;

import com.securenotes.service.PostService;
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
public class PostController {

  private final PostService posts;

  public PostController(PostService posts) {
    this.posts = posts;
  }

  @GetMapping("/api/posts")
  public Map<String, Object> list(@RequestParam Map<String, String> query) {
    return posts.list(query);
  }

  @PostMapping("/api/posts")
  public ResponseEntity<Map<String, Object>> create(
      Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
    return ResponseEntity.status(201).body(posts.create(CurrentUser.get(authentication), body));
  }

  @GetMapping("/api/posts/{id}")
  public Map<String, Object> get(@PathVariable String id) {
    return posts.get(id);
  }

  @PutMapping("/api/posts/{id}")
  public Map<String, Object> update(
      Authentication authentication,
      @PathVariable String id,
      @RequestBody(required = false) Map<String, Object> body) {
    return posts.update(CurrentUser.get(authentication), id, body);
  }

  @DeleteMapping("/api/posts/{id}")
  public Map<String, Object> delete(Authentication authentication, @PathVariable String id) {
    return posts.delete(CurrentUser.get(authentication), id);
  }
}
