package org.demchenko.auth.application;

public interface TelegramInitDataVerifier {
    AuthenticatedTelegramUser verify(String initData);
}
