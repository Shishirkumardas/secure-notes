package com.securenotes.security;

import com.securenotes.error.ApiException;
import com.securenotes.error.JsonErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final JsonErrors errors;

  public JwtAuthFilter(JwtService jwtService, JsonErrors errors) {
    this.jwtService = jwtService;
    this.errors = errors;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    boolean needsAuth = requiresAuth(request);
    String header = request.getHeader("Authorization");
    String token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : "";
    if (!token.isEmpty()) {
      try {
        SecurityContextHolder.getContext().setAuthentication(jwtService.authenticate(token));
      } catch (ApiException ex) {
        SecurityContextHolder.clearContext();
        if (needsAuth || ex.getStatus() >= 500) {
          errors.write(response, ex.getStatus(), ex.getMessage());
          return;
        }
      }
    }
    if (needsAuth && SecurityContextHolder.getContext().getAuthentication() == null) {
      errors.write(response, 401, "Authentication required");
      return;
    }
    try {
      chain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private static boolean requiresAuth(HttpServletRequest request) {
    String path = request.getRequestURI();
    String method = request.getMethod();
    if ("GET".equals(method) && "/api/auth/me".equals(path)) {
      return true;
    }
    return path.equals("/api/notes")
        || path.startsWith("/api/notes/")
        || path.equals("/api/posts")
        || path.startsWith("/api/posts/")
        || path.equals("/api/users")
        || path.startsWith("/api/users/");
  }
}
