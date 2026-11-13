package com.securenotes.domain;

import java.util.Optional;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<UserAccount, ObjectId> {

  Optional<UserAccount> findByEmail(String email);
}
