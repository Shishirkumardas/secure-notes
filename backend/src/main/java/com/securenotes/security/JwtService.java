package com.securenotes.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.securenotes.domain.UserAccount;
import com.securenotes.domain.UserRepository;
import com.securenotes.error.ApiException;
import com.securenotes.mongo.DatabaseLifecycle;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private static final java.util.regex.Pattern OBJECT_ID =
      java.util.regex.Pattern.compile("^[a-fA-F0-9]{24}$");

  private final UserRepository users;
  private final DatabaseLifecycle database;
  private final String secret;
  private final String expiresIn;

  public JwtService(
      UserRepository users,
      DatabaseLifecycle database,
      @Value("${JWT_SECRET:}") String secret,
      @Value("${JWT_EXPIRES_IN:7d}") String expiresIn) {
    this.users = users;
    this.database = database;
    this.secret = secret == null ? "" : secret;
    this.expiresIn = expiresIn;
  }

  public void requireConfigured() {
    if (secret.length() < 16) {
      throw new ApiException(500, "Set JWT_SECRET in Coolify to at least 16 characters, then redeploy.");
    }
  }

  public String sign(UserAccount user) {
    requireConfigured();
    Instant now = Instant.now();
    return JWT.create()
        .withSubject(user.getId().toHexString())
        .withClaim("tv", user.getTokenVersion())
        .withIssuedAt(Date.from(now))
        .withExpiresAt(Date.from(now.plus(ttl())))
        .sign(Algorithm.HMAC256(secret));
  }

  public UsernamePasswordAuthenticationToken authenticate(String token) {
    requireConfigured();
    DecodedJWT jwt;
    try {
      jwt = JWT.require(Algorithm.HMAC256(secret)).build().verify(token);
    } catch (JWTVerificationException ex) {
      throw new ApiException(401, "Authentication required");
    }

    String subject = jwt.getSubject();
    if (subject == null || !OBJECT_ID.matcher(subject).matches()) {
      throw new ApiException(401, "Authentication required");
    }

    database.ensureReady();
    int version = 0;
    if (!jwt.getClaim("tv").isMissing() && !jwt.getClaim("tv").isNull()) {
      version = jwt.getClaim("tv").asInt();
    }
    UserAccount user = users.findById(new ObjectId(subject)).orElse(null);
    if (user == null || version != user.getTokenVersion()) {
      throw new ApiException(401, "Authentication required");
    }
    AuthUser principal = new AuthUser(user);
    return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
  }

  private Duration ttl() {
    String raw = expiresIn == null ? "" : expiresIn.trim();
    if (raw.isEmpty()) {
      return Duration.ofDays(7);
    }
    if (raw.chars().allMatch(Character::isDigit)) {
      return Duration.ofSeconds(Long.parseLong(raw));
    }
    char unit = raw.charAt(raw.length() - 1);
    long amount;
    try {
      amount = Long.parseLong(raw.substring(0, raw.length() - 1).trim());
    } catch (NumberFormatException ex) {
      return Duration.ofDays(7);
    }
    return switch (unit) {
      case 'd' -> Duration.ofDays(amount);
      case 'h' -> Duration.ofHours(amount);
      case 'm' -> Duration.ofMinutes(amount);
      case 's' -> Duration.ofSeconds(amount);
      default -> Duration.ofDays(7);
    };
  }
}
