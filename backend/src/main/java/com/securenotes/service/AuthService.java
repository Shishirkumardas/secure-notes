package com.securenotes.service;

import com.securenotes.domain.UserAccount;
import com.securenotes.domain.UserRepository;
import com.securenotes.error.ApiException;
import com.securenotes.mongo.DatabaseLifecycle;
import com.securenotes.security.JwtService;
import com.securenotes.util.JsonMaps;
import com.securenotes.util.Validators;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private static final String DUMMY_HASH =
      "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
  private static final java.util.regex.Pattern EMAIL =
      java.util.regex.Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  private final DatabaseLifecycle database;

  public AuthService(
      UserRepository users, PasswordEncoder encoder, JwtService jwt, DatabaseLifecycle database) {
    this.users = users;
    this.encoder = encoder;
    this.jwt = jwt;
    this.database = database;
  }

  public Map<String, Object> register(Map<String, Object> rawBody) {
    database.ensureReady();
    Map<String, Object> body = Validators.requireBody(rawBody);
    Instant now = Instant.now();
    UserAccount user = new UserAccount();
    user.setName(Validators.requireName(body.get("name")));
    user.setEmail(Validators.normalizeEmail(body.get("email")));
    user.setPassword(encoder.encode(Validators.requirePassword(body.get("password"))));
    user.setRole("user");
    user.setInterests(
        Validators.normalizeInterests(body.containsKey("interests") ? body.get("interests") : List.of()));
    user.setTokenVersion(0);
    user.setCreatedAt(now);
    user.setUpdatedAt(now);
    try {
      users.save(user);
    } catch (DataIntegrityViolationException ex) {
      throw new ApiException(409, "Email is already in use");
    }
    return tokenBody(user);
  }

  public Map<String, Object> login(Map<String, Object> body) {
    database.ensureReady();
    Object emailRaw = body == null ? null : body.get("email");
    String email = emailRaw instanceof String raw ? raw.trim().toLowerCase(Locale.ROOT) : "";
    Object passwordRaw = body == null ? null : body.get("password");
    String password = passwordRaw instanceof String raw ? raw : "";
    UserAccount user = EMAIL.matcher(email).matches() ? users.findByEmail(email).orElse(null) : null;
    String hash = user == null || user.getPassword() == null ? DUMMY_HASH : user.getPassword();
    boolean matches;
    try {
      matches = encoder.matches(password, hash);
    } catch (RuntimeException ex) {
      matches = false;
    }
    if (user == null || !matches) {
      throw new ApiException(401, "Invalid email or password");
    }
    return tokenBody(user);
  }

  public Map<String, Object> me(UserAccount user) {
    return Map.of("user", JsonMaps.user(user));
  }

  private Map<String, Object> tokenBody(UserAccount user) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("token", jwt.sign(user));
    body.put("user", JsonMaps.user(user));
    return body;
  }
}
