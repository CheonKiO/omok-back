package org.scoula.auth;

public record TokenPair(String accessToken, String refreshToken) {
}
