package com.securenotes.util;

import com.securenotes.domain.Note;
import com.securenotes.domain.Post;
import com.securenotes.domain.UserAccount;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;

public final class JsonMaps {

  private JsonMaps() {}

  public static Map<String, Object> user(UserAccount user) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("id", user.getId().toHexString());
    body.put("name", user.getName());
    body.put("email", user.getEmail());
    body.put("role", user.getRole());
    body.put("interests", user.getInterests() == null ? List.of() : user.getInterests());
    body.put("createdAt", user.getCreatedAt());
    body.put("updatedAt", user.getUpdatedAt());
    return body;
  }

  public static Map<String, Object> note(Note note, UserAccount owner) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("id", note.getId().toHexString());
    body.put("title", note.getTitle());
    body.put("content", note.getContent());
    body.put("owner", owner == null ? idOnly(note.getOwner()) : person(owner));
    body.put("createdAt", note.getCreatedAt());
    body.put("updatedAt", note.getUpdatedAt());
    return body;
  }

  public static Map<String, Object> post(Post post, UserAccount author) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("id", post.getId().toHexString());
    body.put("title", post.getTitle());
    body.put("content", post.getContent());
    body.put("author", author == null ? idOnly(post.getAuthor()) : person(author));
    body.put("createdAt", post.getCreatedAt());
    body.put("updatedAt", post.getUpdatedAt());
    return body;
  }

  public static Map<String, Object> postDocument(Document doc) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("id", id(doc.get("_id")));
    body.put("title", doc.getString("title"));
    body.put("content", doc.getString("content"));
    body.put("author", idOnly(doc.get("author")));
    putInstant(body, "createdAt", doc.get("createdAt"));
    putInstant(body, "updatedAt", doc.get("updatedAt"));
    return body;
  }

  public static Map<String, Object> person(UserAccount user) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("id", user.getId().toHexString());
    body.put("name", user.getName());
    body.put("email", user.getEmail());
    return body;
  }

  public static Map<String, Object> idOnly(Object value) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("id", id(value));
    return body;
  }

  public static String id(Object value) {
    if (value instanceof ObjectId objectId) {
      return objectId.toHexString();
    }
    return value == null ? null : String.valueOf(value);
  }

  public static List<Document> documents(Document parent, String key) {
    Object value = parent.get(key);
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    List<Document> documents = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Document document) {
        documents.add(document);
      }
    }
    return documents;
  }

  private static void putInstant(Map<String, Object> body, String key, Object value) {
    Instant instant = instant(value);
    if (instant != null) {
      body.put(key, instant);
    }
  }

  private static Instant instant(Object value) {
    if (value instanceof Date date) {
      return date.toInstant();
    }
    if (value instanceof Instant instant) {
      return instant;
    }
    return null;
  }
}
