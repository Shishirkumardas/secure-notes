package com.securenotes.service;

import com.securenotes.domain.NoteRepository;
import com.securenotes.domain.PostRepository;
import com.securenotes.domain.UserAccount;
import com.securenotes.domain.UserRepository;
import com.securenotes.error.ApiException;
import com.securenotes.mongo.DatabaseLifecycle;
import com.securenotes.util.JsonMaps;
import com.securenotes.util.Pagination;
import com.securenotes.util.Validators;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

  private final UserRepository users;
  private final NoteRepository notes;
  private final PostRepository posts;
  private final MongoTemplate mongoTemplate;
  private final PasswordEncoder encoder;
  private final DatabaseLifecycle database;
  private final UserAggregation aggregation;

  public UserService(
      UserRepository users,
      NoteRepository notes,
      PostRepository posts,
      MongoTemplate mongoTemplate,
      PasswordEncoder encoder,
      DatabaseLifecycle database,
      UserAggregation aggregation) {
    this.users = users;
    this.notes = notes;
    this.posts = posts;
    this.mongoTemplate = mongoTemplate;
    this.encoder = encoder;
    this.database = database;
    this.aggregation = aggregation;
  }

  public Map<String, Object> list(Map<String, String> query) {
    database.ensureReady();
    Pagination.PageQuery page = Pagination.parse(query);
    Query list = new Query().with(Sort.by(Sort.Direction.DESC, "createdAt"));
    list.skip(page.skip()).limit(page.limit());
    List<UserAccount> found = mongoTemplate.find(list, UserAccount.class);
    long total = mongoTemplate.count(new Query(), UserAccount.class);
    List<Map<String, Object>> data = new ArrayList<>();
    for (UserAccount user : found) {
      data.add(JsonMaps.user(user));
    }
    return pageBody(data, page, total);
  }

  public Map<String, Object> create(Map<String, Object> rawBody) {
    database.ensureReady();
    Map<String, Object> body = Validators.requireBody(rawBody);
    Instant now = Instant.now();
    UserAccount user = new UserAccount();
    user.setName(Validators.requireName(body.get("name")));
    user.setEmail(Validators.normalizeEmail(body.get("email")));
    user.setPassword(encoder.encode(Validators.requirePassword(body.get("password"))));
    user.setRole(body.containsKey("role") ? Validators.requireRole(body.get("role")) : "user");
    user.setInterests(
        Validators.normalizeInterests(body.containsKey("interests") ? body.get("interests") : List.of()));
    user.setTokenVersion(0);
    user.setCreatedAt(now);
    user.setUpdatedAt(now);
    save(user);
    return Map.of("data", JsonMaps.user(user));
  }

  public Map<String, Object> get(String rawId) {
    database.ensureReady();
    ObjectId id = Validators.requireObjectId(rawId, "User id");
    UserAccount user = users.findById(id).orElseThrow(() -> new ApiException(404, "User not found"));
    return Map.of("data", JsonMaps.user(user));
  }

  public Map<String, Object> updateSelf(UserAccount actor, Map<String, Object> rawBody) {
    database.ensureReady();
    Map<String, Object> body = Validators.requireBody(rawBody);
    if (body.containsKey("role")) {
      throw new ApiException(403, "You cannot change your role");
    }
    UserAccount user =
        users.findById(actor.getId()).orElseThrow(() -> new ApiException(401, "Authentication required"));
    apply(user, body, false, actor.getId());
    save(user);
    return Map.of("data", JsonMaps.user(user));
  }

  public Map<String, Object> update(UserAccount actor, String rawId, Map<String, Object> rawBody) {
    database.ensureReady();
    Map<String, Object> body = Validators.requireBody(rawBody);
    ObjectId id = Validators.requireObjectId(rawId, "User id");
    UserAccount user = users.findById(id).orElseThrow(() -> new ApiException(404, "User not found"));
    apply(user, body, true, actor.getId());
    save(user);
    return Map.of("data", JsonMaps.user(user));
  }

  public Map<String, Object> delete(UserAccount actor, String rawId) {
    database.ensureReady();
    ObjectId id = Validators.requireObjectId(rawId, "User id");
    if (id.equals(actor.getId())) {
      throw new ApiException(400, "You cannot delete your own account");
    }
    UserAccount user = users.findById(id).orElseThrow(() -> new ApiException(404, "User not found"));
    notes.deleteByOwner(user.getId());
    posts.deleteByAuthor(user.getId());
    users.delete(user);
    return Map.of("ok", true);
  }

  public Map<String, Object> groupByInterests(Map<String, String> query) {
    database.ensureReady();
    Pagination.PageQuery page = Pagination.parse(query);
    List<Document> rows = aggregation.groupByInterest(page.skip(), page.limit());
    Document result = rows.isEmpty() ? new Document() : rows.get(0);
    int total = 0;
    List<Document> metadata = JsonMaps.documents(result, "metadata");
    if (!metadata.isEmpty() && metadata.get(0).get("total") instanceof Number number) {
      total = number.intValue();
    }
    List<Map<String, Object>> data = new ArrayList<>();
    for (Document group : JsonMaps.documents(result, "data")) {
      List<Map<String, Object>> people = new ArrayList<>();
      for (Document person : JsonMaps.documents(group, "users")) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", JsonMaps.id(person.get("_id")));
        json.put("name", person.getString("name"));
        json.put("email", person.getString("email"));
        people.add(json);
      }
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("interest", group.getString("interest"));
      item.put("count", group.get("count") instanceof Number number ? number.intValue() : 0);
      item.put("users", people);
      data.add(item);
    }
    return pageBody(data, page, total);
  }

  public Map<String, Object> postsForUser(String rawId, Map<String, String> query) {
    database.ensureReady();
    ObjectId id = Validators.requireObjectId(rawId, "User id");
    Pagination.PageQuery page = Pagination.parse(query);
    List<Document> rows = aggregation.postsForUser(id, page.skip(), page.limit());
    if (rows.isEmpty()) {
      throw new ApiException(404, "User not found");
    }
    Document row = rows.get(0);
    int total = row.get("total") instanceof Number number ? number.intValue() : 0;
    List<Map<String, Object>> data = new ArrayList<>();
    for (Document post : JsonMaps.documents(row, "posts")) {
      data.add(JsonMaps.postDocument(post));
    }
    Map<String, Object> user = new LinkedHashMap<>();
    user.put("id", JsonMaps.id(row.get("_id")));
    user.put("name", row.getString("name"));
    user.put("email", row.getString("email"));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("user", user);
    body.put("data", data);
    body.put("pagination", Pagination.meta(page, total));
    return body;
  }

  private void apply(UserAccount user, Map<String, Object> body, boolean allowRole, ObjectId actorId) {
    boolean changed = false;
    if (body.containsKey("name")) {
      user.setName(Validators.requireName(body.get("name")));
      changed = true;
    }
    if (body.containsKey("email")) {
      user.setEmail(Validators.normalizeEmail(body.get("email")));
      changed = true;
    }
    if (body.containsKey("interests")) {
      user.setInterests(Validators.normalizeInterests(body.get("interests")));
      changed = true;
    }
    if (body.containsKey("password")) {
      Object raw = body.get("password");
      if (!(raw instanceof String text) || !text.isEmpty()) {
        user.setPassword(encoder.encode(Validators.requirePassword(raw)));
        user.setTokenVersion(user.getTokenVersion() + 1);
        changed = true;
      }
    }
    if (body.containsKey("role")) {
      if (!allowRole) {
        throw new ApiException(403, "You cannot change your role");
      }
      if (user.getId().equals(actorId)) {
        throw new ApiException(400, "You cannot change your own role");
      }
      user.setRole(Validators.requireRole(body.get("role")));
      changed = true;
    }
    if (!changed) {
      throw new ApiException(400, "Nothing to update");
    }
    user.setUpdatedAt(Instant.now());
  }

  private void save(UserAccount user) {
    try {
      users.save(user);
    } catch (DataIntegrityViolationException ex) {
      throw new ApiException(409, "Email is already in use");
    }
  }

  private static Map<String, Object> pageBody(
      List<Map<String, Object>> data, Pagination.PageQuery page, long total) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("data", data);
    body.put("pagination", Pagination.meta(page, total));
    return body;
  }
}
