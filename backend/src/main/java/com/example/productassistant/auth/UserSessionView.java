package com.example.productassistant.auth;

public record UserSessionView(boolean authenticated, Long userId, String email, int points) {

    public static UserSessionView anonymous() {
        return new UserSessionView(false, null, null, 0);
    }

    public static UserSessionView authenticated(long userId, String email, int points) {
        return new UserSessionView(true, userId, email, points);
    }
}
