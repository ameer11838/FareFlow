package com.fareflow.auth.dto;

import com.fareflow.user.dto.UserResponse;

/**
 * A successful register or login.
 *
 * <p>Carries no password field of any kind — there is no shape in which this API
 * can echo a credential back.
 */
public record AuthResponse(String token, long expiresInSeconds, UserResponse user) {
}
