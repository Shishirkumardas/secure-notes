package com.securenotes.web;

import com.securenotes.service.NoteService;
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
public class NoteController {

  private final NoteService notes;

  public NoteController(NoteService notes) {
    this.notes = notes;
  }

  @GetMapping("/api/notes")
  public Map<String, Object> list(
      Authentication authentication, @RequestParam Map<String, String> query) {
    return notes.list(CurrentUser.get(authentication), query);
  }

  @PostMapping("/api/notes")
  public ResponseEntity<Map<String, Object>> create(
      Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
    return ResponseEntity.status(201).body(notes.create(CurrentUser.get(authentication), body));
  }

  @GetMapping("/api/notes/{id}")
  public Map<String, Object> get(Authentication authentication, @PathVariable String id) {
    return notes.get(CurrentUser.get(authentication), id);
  }

  @PutMapping("/api/notes/{id}")
  public Map<String, Object> update(
      Authentication authentication,
      @PathVariable String id,
      @RequestBody(required = false) Map<String, Object> body) {
    return notes.update(CurrentUser.get(authentication), id, body);
  }

  @DeleteMapping("/api/notes/{id}")
  public Map<String, Object> delete(Authentication authentication, @PathVariable String id) {
    return notes.delete(CurrentUser.get(authentication), id);
  }
}
