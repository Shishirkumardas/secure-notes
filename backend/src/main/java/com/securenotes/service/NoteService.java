package com.securenotes.service;

import com.securenotes.domain.Note;
import com.securenotes.domain.NoteRepository;
import com.securenotes.domain.UserAccount;
import com.securenotes.domain.UserRepository;
import com.securenotes.error.ApiException;
import com.securenotes.mongo.DatabaseLifecycle;
import com.securenotes.util.JsonMaps;
import com.securenotes.util.Pagination;
import com.securenotes.util.Validators;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class NoteService {

  private final NoteRepository notes;
  private final UserRepository users;
  private final MongoTemplate mongoTemplate;
  private final DatabaseLifecycle database;

  public NoteService(
      NoteRepository notes,
      UserRepository users,
      MongoTemplate mongoTemplate,
      DatabaseLifecycle database) {
    this.notes = notes;
    this.users = users;
    this.mongoTemplate = mongoTemplate;
    this.database = database;
  }

  public Map<String, Object> list(UserAccount actor, Map<String, String> query) {
    database.ensureReady();
    Pagination.PageQuery page = Pagination.parse(query);
    Criteria criteria =
        "admin".equals(actor.getRole()) ? new Criteria() : Criteria.where("owner").is(actor.getId());
    Query list = Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "createdAt"));
    list.skip(page.skip()).limit(page.limit());
    List<Note> found = mongoTemplate.find(list, Note.class);
    long total = mongoTemplate.count(Query.query(criteria), Note.class);
    Map<ObjectId, UserAccount> owners = owners(found);
    List<Map<String, Object>> data = new ArrayList<>();
    for (Note note : found) {
      data.add(JsonMaps.note(note, owners.get(note.getOwner())));
    }
    return pageBody(data, page, total);
  }

  public Map<String, Object> create(UserAccount actor, Map<String, Object> rawBody) {
    database.ensureReady();
    Map<String, Object> body = Validators.requireBody(rawBody);
    Instant now = Instant.now();
    Note note = new Note();
    note.setTitle(Validators.requireText(body.get("title"), "Title", 120));
    note.setContent(Validators.requireText(body.get("content"), "Content", 20000));
    note.setOwner(actor.getId());
    note.setCreatedAt(now);
    note.setUpdatedAt(now);
    notes.save(note);
    return Map.of("data", JsonMaps.note(note, actor));
  }

  public Map<String, Object> get(UserAccount actor, String rawId) {
    database.ensureReady();
    ObjectId id = Validators.requireObjectId(rawId, "Note id");
    Note note = notes.findById(id).orElseThrow(() -> new ApiException(404, "Note not found"));
    UserAccount owner = users.findById(note.getOwner()).orElse(null);
    if (owner == null) {
      throw new ApiException(404, "Note not found");
    }
    boolean owns = note.getOwner().equals(actor.getId());
    if (!owns && !"admin".equals(actor.getRole())) {
      throw new ApiException(403, "You cannot view this note");
    }
    return Map.of("data", JsonMaps.note(note, owner));
  }

  public Map<String, Object> update(UserAccount actor, String rawId, Map<String, Object> rawBody) {
    database.ensureReady();
    Map<String, Object> body = Validators.requireBody(rawBody);
    ObjectId id = Validators.requireObjectId(rawId, "Note id");
    Note note = notes.findById(id).orElseThrow(() -> new ApiException(404, "Note not found"));
    if (!note.getOwner().equals(actor.getId())) {
      throw new ApiException(403, "You can only edit your own notes");
    }
    if (!body.containsKey("title") && !body.containsKey("content")) {
      throw new ApiException(400, "Nothing to update");
    }
    if (body.containsKey("title")) {
      note.setTitle(Validators.requireText(body.get("title"), "Title", 120));
    }
    if (body.containsKey("content")) {
      note.setContent(Validators.requireText(body.get("content"), "Content", 20000));
    }
    note.setUpdatedAt(Instant.now());
    notes.save(note);
    UserAccount owner = users.findById(note.getOwner()).orElse(actor);
    return Map.of("data", JsonMaps.note(note, owner));
  }

  public Map<String, Object> delete(UserAccount actor, String rawId) {
    database.ensureReady();
    ObjectId id = Validators.requireObjectId(rawId, "Note id");
    Note note = notes.findById(id).orElseThrow(() -> new ApiException(404, "Note not found"));
    if (!note.getOwner().equals(actor.getId())) {
      throw new ApiException(403, "You can only delete your own notes");
    }
    notes.delete(note);
    return Map.of("ok", true);
  }

  private Map<ObjectId, UserAccount> owners(List<Note> found) {
    List<ObjectId> ids = found.stream().map(Note::getOwner).distinct().toList();
    Map<ObjectId, UserAccount> owners = new HashMap<>();
    for (UserAccount user : users.findAllById(ids)) {
      owners.put(user.getId(), user);
    }
    return owners;
  }

  private static Map<String, Object> pageBody(
      List<Map<String, Object>> data, Pagination.PageQuery page, long total) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("data", data);
    body.put("pagination", Pagination.meta(page, total));
    return body;
  }
}
