package org.scoula.auth.dto;

public record TokenResponse(String accessToken, String refreshToken) {
}
