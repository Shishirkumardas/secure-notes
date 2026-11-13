package com.securenotes.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Pagination {

  public static final int DEFAULT_LIMIT = 10;
  public static final int MAX_LIMIT = 50;
  public static final int MAX_PAGE = 100000;

  private Pagination() {}

  public record PageQuery(int page, int limit, int skip) {}

  public static PageQuery parse(Map<String, String> query) {
    Integer pageRaw = leadingInt(query == null ? null : query.get("page"));
    Integer limitRaw = leadingInt(query == null ? null : query.get("limit"));
    int page = pageRaw != null && pageRaw > 0 ? pageRaw : 1;
    int limit = limitRaw != null && limitRaw > 0 ? limitRaw : DEFAULT_LIMIT;
    if (page > MAX_PAGE) {
      page = MAX_PAGE;
    }
    if (limit > MAX_LIMIT) {
      limit = MAX_LIMIT;
    }
    return new PageQuery(page, limit, (page - 1) * limit);
  }

  public static Map<String, Object> meta(PageQuery page, long total) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("page", page.page());
    body.put("limit", page.limit());
    body.put("total", total);
    body.put("totalPages", total == 0 ? 0 : (int) Math.ceil(total / (double) page.limit()));
    return body;
  }

  static Integer leadingInt(String raw) {
    if (raw == null) {
      return null;
    }
    int index = 0;
    while (index < raw.length() && Character.isWhitespace(raw.charAt(index))) {
      index += 1;
    }
    if (index >= raw.length()) {
      return null;
    }
    int sign = 1;
    char first = raw.charAt(index);
    if (first == '+' || first == '-') {
      sign = first == '-' ? -1 : 1;
      index += 1;
    }
    if (index >= raw.length() || !Character.isDigit(raw.charAt(index))) {
      return null;
    }
    int value = 0;
    while (index < raw.length() && Character.isDigit(raw.charAt(index))) {
      int digit = raw.charAt(index) - '0';
      if (value > (Integer.MAX_VALUE - digit) / 10) {
        return sign > 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
      }
      value = value * 10 + digit;
      index += 1;
    }
    return sign * value;
  }
}
