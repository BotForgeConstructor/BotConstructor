package org.demchenko.auth.application;

public record AuthenticatedTelegramUser(
        long telegramUserId,
        String username,
        String firstName,
        String lastName,
        String languageCode
) {
}
