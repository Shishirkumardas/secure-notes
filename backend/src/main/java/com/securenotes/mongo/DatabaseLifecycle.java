package com.securenotes.mongo;

import com.securenotes.error.ApiException;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseLifecycle {

  private static final Logger log = LoggerFactory.getLogger(DatabaseLifecycle.class);

  private final MongoTemplate mongoTemplate;
  private final IndexService indexes;
  private final AdminSeeder seeder;
  private final AtomicBoolean ready = new AtomicBoolean(false);
  private final Object lock = new Object();

  public DatabaseLifecycle(MongoTemplate mongoTemplate, IndexService indexes, AdminSeeder seeder) {
    this.mongoTemplate = mongoTemplate;
    this.indexes = indexes;
    this.seeder = seeder;
  }

  @PostConstruct
  void start() {
    Thread thread = new Thread(this::connectForever, "database-ready");
    thread.setDaemon(true);
    thread.start();
  }

  public boolean isConnected() {
    try {
      mongoTemplate.executeCommand("{ ping: 1 }");
      return true;
    } catch (RuntimeException ex) {
      return false;
    }
  }

  public void ensureReady() {
    if (ready.get()) {
      return;
    }
    synchronized (lock) {
      if (ready.get()) {
        return;
      }
      try {
        mongoTemplate.executeCommand("{ ping: 1 }");
        indexes.ensure();
        seeder.seed();
        ready.set(true);
        log.info("Database ready");
      } catch (ApiException ex) {
        throw ex;
      } catch (RuntimeException ex) {
        String message = ex.getMessage() == null ? "Database connection failed" : ex.getMessage();
        throw new ApiException(503, message);
      }
    }
  }

  private void connectForever() {
    while (!ready.get() && !Thread.currentThread().isInterrupted()) {
      try {
        ensureReady();
      } catch (RuntimeException ex) {
        log.error(ex.getMessage());
        try {
          Thread.sleep(5000);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }
}
