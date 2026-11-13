package com.securenotes.util;

import com.securenotes.error.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bson.types.ObjectId;

public final class Validators {

  private static final java.util.regex.Pattern EMAIL =
      java.util.regex.Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private static final java.util.regex.Pattern OBJECT_ID =
      java.util.regex.Pattern.compile("^[a-fA-F0-9]{24}$");

  private Validators() {}

  public static Map<String, Object> requireBody(Map<String, Object> body) {
    if (body == null) {
      throw new ApiException(400, "Request body must be a JSON object");
    }
    return body;
  }

  public static String requireName(Object name) {
    if (!(name instanceof String raw) || raw.trim().isEmpty()) {
      throw new ApiException(400, "Name is required");
    }
    String value = raw.trim();
    if (value.length() > 80) {
      throw new ApiException(400, "Name must be 80 characters or fewer");
    }
    return value;
  }

  public static String normalizeEmail(Object email) {
    if (!(email instanceof String raw)) {
      throw new ApiException(400, "Email is required");
    }
    String value = raw.trim().toLowerCase(Locale.ROOT);
    if (!EMAIL.matcher(value).matches()) {
      throw new ApiException(400, "Email is invalid");
    }
    if (value.length() > 254) {
      throw new ApiException(400, "Email must be 254 characters or fewer");
    }
    return value;
  }

  public static String requirePassword(Object password) {
    if (!(password instanceof String raw) || raw.length() < 8) {
      throw new ApiException(400, "Password must be at least 8 characters");
    }
    if (raw.length() > 72) {
      throw new ApiException(400, "Password must be 72 characters or fewer");
    }
    return raw;
  }

  public static List<String> normalizeInterests(Object interests) {
    if (!(interests instanceof List<?> list)) {
      throw new ApiException(400, "Interests must be an array of strings");
    }
    List<String> cleaned = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (Object value : list) {
      if (!(value instanceof String raw)) {
        throw new ApiException(400, "Each interest must be a string");
      }
      String interest = raw.trim().toLowerCase(Locale.ROOT);
      if (interest.isEmpty()) {
        continue;
      }
      if (interest.length() > 40) {
        throw new ApiException(400, "Each interest must be 40 characters or fewer");
      }
      if (seen.add(interest)) {
        cleaned.add(interest);
      }
    }
    if (cleaned.size() > 20) {
      throw new ApiException(400, "A profile can have at most 20 interests");
    }
    return cleaned;
  }

  public static String requireText(Object value, String label, int max) {
    if (!(value instanceof String raw) || raw.trim().isEmpty()) {
      throw new ApiException(400, label + " is required");
    }
    String text = raw.trim();
    if (text.length() > max) {
      throw new ApiException(400, label + " must be " + max + " characters or fewer");
    }
    return text;
  }

  public static String requireRole(Object role) {
    if (!"user".equals(role) && !"admin".equals(role)) {
      throw new ApiException(400, "Role must be user or admin");
    }
    return (String) role;
  }

  public static ObjectId requireObjectId(String value, String label) {
    if (value == null || !OBJECT_ID.matcher(value).matches()) {
      throw new ApiException(400, label + " is invalid");
    }
    return new ObjectId(value);
  }
}
