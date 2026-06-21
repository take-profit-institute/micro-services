package org.profit.candle.auth.google.client;

public record GoogleProfile(String subject, String email, boolean emailVerified) {
}
