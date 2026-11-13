package com.securenotes.service;

import com.securenotes.domain.Post;
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
public class PostService {

  private final PostRepository posts;
  private final UserRepository users;
  private final MongoTemplate mongoTemplate;
  private final DatabaseLifecycle database;

  public PostService(
      PostRepository posts,
      UserRepository users,
      MongoTemplate mongoTemplate,
      DatabaseLifecycle database) {
    this.posts = posts;
    this.users = users;
    this.mongoTemplate = mongoTemplate;
    this.database = database;
  }

  public Map<String, Object> list(Map<String, String> query) {
    database.ensureReady();
    Pagination.PageQuery page = Pagination.parse(query);
    Query list = new Query().with(Sort.by(Sort.Direction.DESC, "createdAt"));
    list.skip(page.skip()).limit(page.limit());
    List<Post> found = mongoTemplate.find(list, Post.class);
    long total = mongoTemplate.count(new Query(), Post.class);
    Map<ObjectId, UserAccount> authors = authors(found);
    List<Map<String, Object>> data = new ArrayList<>();
    for (Post post : found) {
      data.add(JsonMaps.post(post, authors.get(post.getAuthor())));
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("data", data);
    body.put("pagination", Pagination.meta(page, total));
    return body;
  }

  public Map<String, Object> create(UserAccount actor, Map<String, Object> rawBody) {
    database.ensureReady();
    Map<String, Object> body = Validators.requireBody(rawBody);
    Instant now = Instant.now();
    Post post = new Post();
    post.setTitle(Validators.requireText(body.get("title"), "Title", 120));
    post.setContent(Validators.requireText(body.get("content"), "Content", 20000));
    post.setAuthor(actor.getId());
    post.setCreatedAt(now);
    post.setUpdatedAt(now);
    posts.save(post);
    return Map.of("data", JsonMaps.post(post, actor));
  }

  public Map<String, Object> get(String rawId) {
    database.ensureReady();
    ObjectId id = Validators.requireObjectId(rawId, "Post id");
    Post post = posts.findById(id).orElseThrow(() -> new ApiException(404, "Post not found"));
    UserAccount author = users.findById(post.getAuthor()).orElse(null);
    return Map.of("data", JsonMaps.post(post, author));
  }

  public Map<String, Object> update(UserAccount actor, String rawId, Map<String, Object> rawBody) {
    database.ensureReady();
    Map<String, Object> body = Validators.requireBody(rawBody);
    ObjectId id = Validators.requireObjectId(rawId, "Post id");
    Post post = posts.findById(id).orElseThrow(() -> new ApiException(404, "Post not found"));
    if (!post.getAuthor().equals(actor.getId())) {
      throw new ApiException(403, "You can only edit your own posts");
    }
    if (!body.containsKey("title") && !body.containsKey("content")) {
      throw new ApiException(400, "Nothing to update");
    }
    if (body.containsKey("title")) {
      post.setTitle(Validators.requireText(body.get("title"), "Title", 120));
    }
    if (body.containsKey("content")) {
      post.setContent(Validators.requireText(body.get("content"), "Content", 20000));
    }
    post.setUpdatedAt(Instant.now());
    posts.save(post);
    return Map.of("data", JsonMaps.post(post, actor));
  }

  public Map<String, Object> delete(UserAccount actor, String rawId) {
    database.ensureReady();
    ObjectId id = Validators.requireObjectId(rawId, "Post id");
    Post post = posts.findById(id).orElseThrow(() -> new ApiException(404, "Post not found"));
    if (!post.getAuthor().equals(actor.getId())) {
      throw new ApiException(403, "You can only delete your own posts");
    }
    posts.delete(post);
    return Map.of("ok", true);
  }

  private Map<ObjectId, UserAccount> authors(List<Post> found) {
    List<ObjectId> ids = found.stream().map(Post::getAuthor).distinct().toList();
    Map<ObjectId, UserAccount> authors = new HashMap<>();
    for (UserAccount user : users.findAllById(ids)) {
      authors.put(user.getId(), user);
    }
    return authors;
  }
}
