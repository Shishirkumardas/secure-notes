package com.securenotes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securenotes.domain.Note;
import com.securenotes.domain.NoteRepository;
import com.securenotes.domain.Post;
import com.securenotes.domain.PostRepository;
import com.securenotes.domain.UserAccount;
import com.securenotes.domain.UserRepository;
import com.securenotes.mongo.IndexService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    properties = {
      "JWT_SECRET=test-secret-value-long",
      "JWT_EXPIRES_IN=1h",
      "ADMIN_EMAIL=",
      "ADMIN_PASSWORD=",
      "de.flapdoodle.mongodb.embedded.version=7.0.14"
    })
@AutoConfigureMockMvc
class ApiTests {

  private static final AtomicInteger SEQUENCE = new AtomicInteger();

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository users;
  @Autowired private NoteRepository notes;
  @Autowired private PostRepository posts;
  @Autowired private PasswordEncoder encoder;
  @Autowired private MongoTemplate mongoTemplate;
  @Autowired private IndexService indexes;

  @BeforeEach
  void clean() {
    indexes.ensure();
    posts.deleteAll();
    notes.deleteAll();
    users.deleteAll();
  }

  @Test
  void registersANormalUserAndHashesThePassword() throws Exception {
    Map<String, Object> body = registerBody();
    body.put("role", "admin");
    body.put("interests", List.of("Chess", "chess", ""));
    JsonNode created = json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
        .andExpect(status().isCreated())
        .andReturn());
    assertEquals("user", created.get("user").get("role").asText());
    assertEquals("chess", created.get("user").get("interests").get(0).asText());
    assertEquals(1, created.get("user").get("interests").size());
    String raw = created.toString();
    assertFalse(raw.contains(String.valueOf(body.get("password"))));
    assertFalse(raw.contains("$2"));

    UserAccount stored = users.findByEmail(body.get("email").toString()).orElseThrow();
    assertNotEquals(body.get("password"), stored.getPassword());
    assertTrue(stored.getPassword().startsWith("$2"));

    JsonNode me = json(mvc.perform(get("/api/auth/me").header("Authorization", bearer(created)))
        .andExpect(status().isOk())
        .andReturn());
    assertEquals(body.get("email"), me.get("user").get("email").asText());
  }

  @Test
  void rejectsDuplicateEmailsAndBadLogins() throws Exception {
    Map<String, Object> body = registerBody();
    mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
        .andExpect(status().isCreated());
    mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
        .andExpect(status().isConflict());
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("email", body.get("email"), "password", "wrong-password"))))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/notes")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void passwordChangeInvalidatesThePreviousToken() throws Exception {
    JsonNode created = register();
    String token = created.get("token").asText();
    JsonNode updated = json(mvc.perform(put("/api/users/me").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("password", "newpassword1"))))
        .andExpect(status().isOk())
        .andReturn());
    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    JsonNode fresh = json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("email", updated.get("data").get("email").asText(), "password", "newpassword1"))))
        .andExpect(status().isOk())
        .andReturn());
    mvc.perform(get("/api/auth/me").header("Authorization", bearer(fresh))).andExpect(status().isOk());
  }

  @Test
  void usersManageOnlyTheirOwnNotesAndAdminsCanRead() throws Exception {
    JsonNode ada = register();
    JsonNode grace = createAdmin();
    JsonNode created = json(mvc.perform(post("/api/notes").header("Authorization", bearer(ada))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("title", "Private", "content", "Ada only"))))
        .andExpect(status().isCreated())
        .andReturn());
    JsonNode ownList = json(mvc.perform(get("/api/notes").header("Authorization", bearer(ada)))
        .andExpect(status().isOk())
        .andReturn());
    assertEquals(1, ownList.get("data").size());
    JsonNode adminList = json(mvc.perform(get("/api/notes").header("Authorization", bearer(grace)))
        .andExpect(status().isOk())
        .andReturn());
    assertEquals(1, adminList.get("data").size());

    JsonNode other = registerNamed("Alan");
    JsonNode otherList = json(mvc.perform(get("/api/notes").header("Authorization", bearer(other)))
        .andExpect(status().isOk())
        .andReturn());
    assertEquals(0, otherList.get("data").size());

    String noteId = created.get("data").get("id").asText();
    mvc.perform(get("/api/notes/" + noteId).header("Authorization", bearer(other)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/notes/" + noteId).header("Authorization", bearer(grace)))
        .andExpect(status().isOk());
    mvc.perform(put("/api/notes/" + noteId).header("Authorization", bearer(grace))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("title", "Hijack"))))
        .andExpect(status().isForbidden());
    JsonNode edited = json(mvc.perform(put("/api/notes/" + noteId).header("Authorization", bearer(ada))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("title", "Revised"))))
        .andExpect(status().isOk())
        .andReturn());
    assertEquals("Revised", edited.get("data").get("title").asText());
    mvc.perform(delete("/api/notes/" + noteId).header("Authorization", bearer(ada)))
        .andExpect(status().isOk());
  }

  @Test
  void noteListsArePaginated() throws Exception {
    JsonNode ada = register();
    for (int index = 0; index < 12; index += 1) {
      mvc.perform(post("/api/notes").header("Authorization", bearer(ada))
              .contentType(MediaType.APPLICATION_JSON)
              .content(json(Map.of("title", "Note " + index, "content", "Body " + index))))
          .andExpect(status().isCreated());
      Thread.sleep(30);
    }
    JsonNode page = json(mvc.perform(get("/api/notes?page=1&limit=5").header("Authorization", bearer(ada)))
        .andExpect(status().isOk())
        .andReturn());
    assertEquals(5, page.get("data").size());
    assertEquals(12, page.get("pagination").get("total").asInt());
    assertEquals(3, page.get("pagination").get("totalPages").asInt());
    assertEquals("Note 11", page.get("data").get(0).get("title").asText());
    JsonNode last = json(mvc.perform(get("/api/notes?page=3&limit=5").header("Authorization", bearer(ada)))
        .andExpect(status().isOk())
        .andReturn());
    assertEquals(2, last.get("data").size());
    assertEquals("Note 0", last.get("data").get(1).get("title").asText());
    JsonNode capped = json(mvc.perform(get("/api/notes?page=-4&limit=999").header("Authorization", bearer(ada)))
        .andExpect(status().isOk())
        .andReturn());
    assertEquals(1, capped.get("pagination").get("page").asInt());
    assertEquals(50, capped.get("pagination").get("limit").asInt());
  }

  @Test
  void postsAreVisibleAndOnlyTheAuthorCanChangeThem() throws Exception {
    JsonNode ada = register();
    JsonNode alan = registerNamed("Alan");
    JsonNode created = json(mvc.perform(post("/api/posts").header("Authorization", bearer(ada))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("title", "Hello", "content", "World"))))
        .andExpect(status().isCreated())
        .andReturn());
    JsonNode list = json(mvc.perform(get("/api/posts").header("Authorization", bearer(alan)))
        .andExpect(status().isOk())
        .andReturn());
    assertEquals(1, list.get("data").size());
    assertEquals("Ada Lovelace", list.get("data").get(0).get("author").get("name").asText());
    mvc.perform(put("/api/posts/" + created.get("data").get("id").asText())
            .header("Authorization", bearer(alan))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("title", "Nope"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminsManageUsersAndDemotionAppliesImmediately() throws Exception {
    JsonNode admin = createAdmin();
    String secondEmail = nextEmail("second");
    JsonNode created = json(mvc.perform(post("/api/users").header("Authorization", bearer(admin))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "name", "Second Admin",
                "email", secondEmail,
                "password", "password123",
                "role", "admin",
                "interests", List.of("music", "Music")))))
        .andExpect(status().isCreated())
        .andReturn());
    assertEquals("music", created.get("data").get("interests").get(0).asText());
    assertEquals(1, created.get("data").get("interests").size());
    JsonNode login = json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("email", secondEmail, "password", "password123"))))
        .andExpect(status().isOk())
        .andReturn());
    JsonNode regular = register();
    mvc.perform(get("/api/users").header("Authorization", bearer(regular))).andExpect(status().isForbidden());
    mvc.perform(put("/api/users/" + created.get("data").get("id").asText())
            .header("Authorization", bearer(admin))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("role", "user"))))
        .andExpect(status().isOk());
    mvc.perform(get("/api/users").header("Authorization", bearer(login))).andExpect(status().isForbidden());
    mvc.perform(delete("/api/users/" + admin.get("user").get("id").asText())
            .header("Authorization", bearer(admin)))
        .andExpect(status().isBadRequest());

    JsonNode note = json(mvc.perform(post("/api/notes").header("Authorization", bearer(regular))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("title", "Gone", "content", "Soon"))))
        .andExpect(status().isCreated())
        .andReturn());
    JsonNode post = json(mvc.perform(post("/api/posts").header("Authorization", bearer(regular))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("title", "Gone post", "content", "Soon"))))
        .andExpect(status().isCreated())
        .andReturn());
    mvc.perform(delete("/api/users/" + regular.get("user").get("id").asText())
            .header("Authorization", bearer(admin)))
        .andExpect(status().isOk());
    assertFalse(notes.findById(new ObjectId(note.get("data").get("id").asText())).isPresent());
    assertFalse(posts.findById(new ObjectId(post.get("data").get("id").asText())).isPresent());

    JsonNode list = json(mvc.perform(get("/api/users?page=1&limit=1").header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andReturn());
    assertEquals(1, list.get("pagination").get("limit").asInt());
    assertFalse(list.toString().contains("$2"));
  }

  @Test
  void keepsTheSamePasswordWhenAnAdminUpdatesAName() throws Exception {
    JsonNode admin = createAdmin();
    JsonNode ada = register();
    mvc.perform(put("/api/users/" + ada.get("user").get("id").asText())
            .header("Authorization", bearer(admin))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("name", "Ada Renamed"))))
        .andExpect(status().isOk());
    JsonNode login = json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("email", ada.get("user").get("email").asText(), "password", "password123"))))
        .andExpect(status().isOk())
        .andReturn());
    assertEquals("Ada Renamed", login.get("user").get("name").asText());
  }

  @Test
  void groupsUsersByInterestWithOneAggregateCall() throws Exception {
    assertAggregateShape();
    JsonNode admin = createAdmin();
    registerWithInterests("Ada", List.of("Chess", "reading"));
    registerWithInterests("Alan", List.of("chess", "music"));
    registerWithInterests("Empty", List.of());
    JsonNode outsider = registerWithInterests("Outsider", List.of());
    mvc.perform(get("/api/users/interests").header("Authorization", bearer(outsider)))
        .andExpect(status().isForbidden());
    JsonNode grouped = json(mvc.perform(get("/api/users/interests?page=1&limit=10")
            .header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andReturn());
    Map<String, JsonNode> byName = new LinkedHashMap<>();
    List<String> names = new ArrayList<>();
    for (JsonNode group : grouped.get("data")) {
      byName.put(group.get("interest").asText(), group);
      names.add(group.get("interest").asText());
    }
    assertEquals(3, byName.get("chess").get("count").asInt());
    assertEquals(1, byName.get("reading").get("count").asInt());
    assertEquals(1, byName.get("music").get("count").asInt());
    assertEquals(List.of("chess", "music", "reading"), names);
    assertFalse(grouped.toString().contains("Empty"));
    assertFalse(grouped.toString().contains("$2"));
  }

  @Test
  void returnsOneUsersPostsThroughALookup() throws Exception {
    JsonNode ada = registerNamed("Ada");
    JsonNode alan = registerNamed("Alan");
    mvc.perform(post("/api/posts").header("Authorization", bearer(ada))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("title", "One", "content", "A"))))
        .andExpect(status().isCreated());
    Thread.sleep(30);
    mvc.perform(post("/api/posts").header("Authorization", bearer(ada))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("title", "Two", "content", "B"))))
        .andExpect(status().isCreated());
    mvc.perform(post("/api/posts").header("Authorization", bearer(alan))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("title", "Other", "content", "C"))))
        .andExpect(status().isCreated());

    JsonNode found = json(mvc.perform(get("/api/users/" + ada.get("user").get("id").asText() + "/posts?page=1&limit=10")
            .header("Authorization", bearer(alan)))
        .andExpect(status().isOk())
        .andReturn());
    assertEquals("Ada", found.get("user").get("name").asText());
    assertEquals(List.of("Two", "One"), List.of(
        found.get("data").get(0).get("title").asText(),
        found.get("data").get(1).get("title").asText()));
    assertEquals(2, found.get("pagination").get("total").asInt());

    mvc.perform(get("/api/users/" + new ObjectId().toHexString() + "/posts")
            .header("Authorization", bearer(alan)))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/users/not-an-id/posts").header("Authorization", bearer(alan)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void declaresOnlyTheIndexesTheQueriesUse() throws Exception {
    indexes.ensure();
    assertEquals(
        List.of("_id_", "createdAt_-1", "email_1", "interests_1"), indexNames("users"));
    assertEquals(
        List.of("_id_", "createdAt_-1", "owner_1_createdAt_-1"), indexNames("notes"));
    assertEquals(List.of("_id_", "author_1", "createdAt_-1"), indexNames("posts"));
    Document emailIndex = mongoTemplate.getCollection("users").listIndexes().into(new ArrayList<>()).stream()
        .filter(index -> "email_1".equals(index.getString("name")))
        .findFirst()
        .orElseThrow();
    assertEquals(Boolean.TRUE, emailIndex.getBoolean("unique"));
    assertAggregateShape();
  }

  @Test
  void listAndLookupQueriesAreIndexScans() throws Exception {
    JsonNode ada = registerWithInterests("Ada", List.of("chess"));
    ObjectId userId = new ObjectId(ada.get("user").get("id").asText());
    Instant now = Instant.now();
    Note note = new Note();
    note.setTitle("T");
    note.setContent("C");
    note.setOwner(userId);
    note.setCreatedAt(now);
    note.setUpdatedAt(now);
    notes.save(note);
    Post post = new Post();
    post.setTitle("P");
    post.setContent("C");
    post.setAuthor(userId);
    post.setCreatedAt(now);
    post.setUpdatedAt(now);
    posts.save(post);

    assertIndexUsed(
        mongoTemplate.getCollection("users").find(new Document("email", ada.get("user").get("email").asText())).explain(),
        "email_1");
    assertIndexUsed(
        mongoTemplate.getCollection("users").find().sort(new Document("createdAt", -1)).skip(0).limit(10).explain(),
        "createdAt_-1");
    assertIndexUsed(
        mongoTemplate.getCollection("notes")
            .find(new Document("owner", userId))
            .sort(new Document("createdAt", -1))
            .skip(0)
            .limit(10)
            .explain(),
        "owner_1_createdAt_-1");
    assertIndexUsed(
        mongoTemplate.getCollection("notes").find().sort(new Document("createdAt", -1)).skip(0).limit(10).explain(),
        "createdAt_-1");
    assertIndexUsed(
        mongoTemplate.getCollection("posts").find().sort(new Document("createdAt", -1)).skip(0).limit(10).explain(),
        "createdAt_-1");
    assertIndexUsed(
        mongoTemplate.getCollection("posts").find(new Document("author", userId)).explain(),
        "author_1");

    Document grouped = mongoTemplate.getCollection("users").aggregate(List.of(
            new Document("$match", new Document("interests", new Document("$gte", ""))),
            new Document("$project", new Document("name", 1).append("email", 1).append("interests", 1)),
            new Document("$unwind", "$interests"),
            new Document("$group", new Document("_id", "$interests").append("count", new Document("$sum", 1)))))
        .explain();
    String groupedText = grouped.toJson();
    assertTrue(groupedText.contains("interests_1"), groupedText);
    assertTrue(groupedText.contains("IXSCAN"), groupedText);

    Document lookup = mongoTemplate.getCollection("users").aggregate(List.of(
            new Document("$match", new Document("_id", userId)),
            new Document("$lookup", new Document("from", "posts")
                .append("localField", "_id")
                .append("foreignField", "author")
                .append("as", "posts"))))
        .explain();
    String lookupText = lookup.toJson();
    assertTrue(lookupText.contains("author_1"), lookupText);
  }

  @Test
  void healthStaysAvailable() throws Exception {
    JsonNode health = json(mvc.perform(get("/api/health")).andExpect(status().isOk()).andReturn());
    assertTrue(health.get("ok").asBoolean());
    assertEquals("connected", health.get("db").asText());
    mvc.perform(get("/api/does-not-exist")).andExpect(status().isNotFound());
  }

  private void assertAggregateShape() throws Exception {
    int aggregates = 0;
    int lookups = 0;
    int groups = 0;
    try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
      for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
        String code = strip(Files.readString(path));
        int found = count(code, ".aggregate(");
        aggregates += found;
        if (found > 0) {
          assertTrue(path.endsWith("UserAggregation.java"), path.toString());
          assertEquals(2, found);
        }
        assertFalse(code.contains(".hint("), path.toString());
        lookups += count(code, "$lookup");
        groups += count(code, "$group");
      }
    }
    assertEquals(2, aggregates);
    assertEquals(1, lookups);
    assertEquals(1, groups);
  }

  private static String strip(String source) {
    return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
  }

  private static int count(String text, String needle) {
    int found = 0;
    int from = 0;
    while (from < text.length()) {
      int at = text.indexOf(needle, from);
      if (at < 0) {
        return found;
      }
      found += 1;
      from = at + needle.length();
    }
    return found;
  }

  private List<String> indexNames(String collection) {
    return mongoTemplate.getCollection(collection).listIndexes().into(new ArrayList<>()).stream()
        .map(index -> index.getString("name"))
        .sorted()
        .toList();
  }

  private static void assertIndexUsed(Document explained, String indexName) {
    String text = explained.toJson();
    assertTrue(usesIndex(explained, indexName), text);
  }

  private static boolean usesIndex(Object node, String indexName) {
    if (node instanceof Document document) {
      if ("IXSCAN".equals(document.getString("stage")) && indexName.equals(document.getString("indexName"))) {
        return true;
      }
      for (Object value : document.values()) {
        if (usesIndex(value, indexName)) {
          return true;
        }
      }
    } else if (node instanceof List<?> list) {
      for (Object value : list) {
        if (usesIndex(value, indexName)) {
          return true;
        }
      }
    }
    return false;
  }

  private JsonNode register() throws Exception {
    return registerNamed("Ada Lovelace");
  }

  private JsonNode registerNamed(String name) throws Exception {
    Map<String, Object> body = registerBody();
    body.put("name", name);
    return json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
        .andExpect(status().isCreated())
        .andReturn());
  }

  private JsonNode registerWithInterests(String name, List<String> interests) throws Exception {
    Map<String, Object> body = registerBody();
    body.put("name", name);
    body.put("interests", interests);
    return json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
        .andExpect(status().isCreated())
        .andReturn());
  }

  private Map<String, Object> registerBody() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "Ada Lovelace");
    body.put("email", nextEmail("ada"));
    body.put("password", "password123");
    body.put("interests", List.of("reading"));
    return body;
  }

  private JsonNode createAdmin() throws Exception {
    String email = nextEmail("admin");
    Instant now = Instant.now();
    UserAccount admin = new UserAccount();
    admin.setName("Grace Hopper");
    admin.setEmail(email);
    admin.setPassword(encoder.encode("password123"));
    admin.setRole("admin");
    admin.setInterests(List.of("chess"));
    admin.setTokenVersion(0);
    admin.setCreatedAt(now);
    admin.setUpdatedAt(now);
    users.save(admin);
    return json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("email", email, "password", "password123"))))
        .andExpect(status().isOk())
        .andReturn());
  }

  private static String nextEmail(String prefix) {
    return prefix + SEQUENCE.incrementAndGet() + "@example.com";
  }

  private static String bearer(JsonNode body) {
    JsonNode token = body.get("token");
    return "Bearer " + token.asText();
  }

  private String json(Object body) throws Exception {
    return objectMapper.writeValueAsString(body);
  }

  private JsonNode json(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }
}
