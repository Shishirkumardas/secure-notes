package com.securenotes.web;

import com.securenotes.domain.UserAccount;
import com.securenotes.security.AuthUser;
import org.springframework.security.core.Authentication;

public final class CurrentUser {

  private CurrentUser() {}

  public static UserAccount get(Authentication authentication) {
    return ((AuthUser) authentication.getPrincipal()).getUser();
  }
}
