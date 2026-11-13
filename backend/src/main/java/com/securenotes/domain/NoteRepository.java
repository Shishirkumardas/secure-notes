package com.securenotes.domain;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NoteRepository extends MongoRepository<Note, ObjectId> {

  void deleteByOwner(ObjectId owner);
}
