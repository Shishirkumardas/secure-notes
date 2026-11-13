package com.securenotes.service;

import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserAggregation {

  private final MongoTemplate mongoTemplate;

  public UserAggregation(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  public List<Document> groupByInterest(int skip, int limit) {
    List<Document> pipeline =
        List.of(
            new Document("$match", new Document("interests", new Document("$gte", ""))),
            new Document(
                "$project", new Document("name", 1).append("email", 1).append("interests", 1)),
            new Document("$unwind", "$interests"),
            new Document(
                "$group",
                new Document("_id", "$interests")
                    .append("count", new Document("$sum", 1))
                    .append(
                        "users",
                        new Document(
                            "$push",
                            new Document("_id", "$_id").append("name", "$name").append("email", "$email")))),
            new Document("$sort", new Document("_id", 1)),
            new Document(
                "$facet",
                new Document("metadata", List.of(new Document("$count", "total")))
                    .append(
                        "data",
                        List.of(
                            new Document("$skip", skip),
                            new Document("$limit", limit),
                            new Document(
                                "$project",
                                new Document("_id", 0)
                                    .append("interest", "$_id")
                                    .append("count", 1)
                                    .append("users", 1))))));
    return mongoTemplate.getCollection("users").aggregate(pipeline).into(new ArrayList<>());
  }

  public List<Document> postsForUser(ObjectId id, int skip, int limit) {
    List<Document> pipeline =
        List.of(
            new Document("$match", new Document("_id", id)),
            new Document("$project", new Document("name", 1).append("email", 1)),
            new Document(
                "$lookup",
                new Document("from", "posts")
                    .append("localField", "_id")
                    .append("foreignField", "author")
                    .append("as", "posts")),
            new Document(
                "$project",
                new Document("name", 1)
                    .append("email", 1)
                    .append("total", new Document("$size", "$posts"))
                    .append(
                        "posts",
                        new Document(
                            "$slice",
                            List.of(
                                new Document(
                                    "$sortArray",
                                    new Document("input", "$posts")
                                        .append("sortBy", new Document("createdAt", -1))),
                                skip,
                                limit)))));
    return mongoTemplate.getCollection("users").aggregate(pipeline).into(new ArrayList<>());
  }
}
