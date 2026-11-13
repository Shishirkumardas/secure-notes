package com.securenotes.mongo;

import com.securenotes.domain.UserAccount;
import com.securenotes.domain.UserRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminSeeder {

  private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final Environment environment;

  public AdminSeeder(UserRepository users, PasswordEncoder encoder, Environment environment) {
    this.users = users;
    this.encoder = encoder;
    this.environment = environment;
  }

  public void seed() {
    String email = environment.getProperty("ADMIN_EMAIL", "").trim().toLowerCase();
    String password = environment.getProperty("ADMIN_PASSWORD", "");
    String name = environment.getProperty("ADMIN_NAME", "Admin").trim();
    if (name.isEmpty()) {
      name = "Admin";
    }
    if (email.isEmpty() || password.isEmpty()) {
      log.info("Admin seed skipped. Set ADMIN_EMAIL and ADMIN_PASSWORD to create one.");
      return;
    }
    if (password.length() < 8) {
      throw new IllegalStateException("ADMIN_PASSWORD must be at least 8 characters");
    }
    if (users.findByEmail(email).isPresent()) {
      return;
    }

    Instant now = Instant.now();
    UserAccount admin = new UserAccount();
    admin.setName(name);
    admin.setEmail(email);
    admin.setPassword(encoder.encode(password));
    admin.setRole("admin");
    admin.setInterests(List.of());
    admin.setTokenVersion(0);
    admin.setCreatedAt(now);
    admin.setUpdatedAt(now);
    try {
      users.save(admin);
      log.info("Seeded admin user {}", email);
    } catch (DataIntegrityViolationException ex) {
      log.info("Admin user {} already exists", email);
    }
  }
}
