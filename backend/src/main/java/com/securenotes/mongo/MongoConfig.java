package com.securenotes.mongo;

import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoConfig {

  @Bean
  public MongoClientSettingsBuilderCustomizer mongoTimeouts() {
    return builder ->
        builder.applyToClusterSettings(
            cluster -> cluster.serverSelectionTimeout(5, TimeUnit.SECONDS));
  }
}
