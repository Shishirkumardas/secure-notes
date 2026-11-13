package com.securenotes.mongo;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Service;

@Service
public class IndexService {

  private final MongoTemplate mongoTemplate;

  public IndexService(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  public void ensure() {
    var users = mongoTemplate.indexOps("users");
    users.createIndex(new Index().on("email", Sort.Direction.ASC).unique().named("email_1"));
    users.createIndex(new Index().on("createdAt", Sort.Direction.DESC).named("createdAt_-1"));
    users.createIndex(new Index().on("interests", Sort.Direction.ASC).named("interests_1"));

    var notes = mongoTemplate.indexOps("notes");
    // A user's list filters by owner and then sorts by createdAt, so owner is the prefix.
    notes.createIndex(
        new Index()
            .on("owner", Sort.Direction.ASC)
            .on("createdAt", Sort.Direction.DESC)
            .named("owner_1_createdAt_-1"));
    // The admin list has no owner filter, so createdAt has to be its own index.
    notes.createIndex(new Index().on("createdAt", Sort.Direction.DESC).named("createdAt_-1"));

    var posts = mongoTemplate.indexOps("posts");
    posts.createIndex(new Index().on("author", Sort.Direction.ASC).named("author_1"));
    posts.createIndex(new Index().on("createdAt", Sort.Direction.DESC).named("createdAt_-1"));
  }
}
