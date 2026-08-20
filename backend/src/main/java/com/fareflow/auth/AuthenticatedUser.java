package com.fareflow.auth;

/** The principal placed in the security context by {@link JwtAuthenticationFilter}. */
public record AuthenticatedUser(long userId, String email) {
}
