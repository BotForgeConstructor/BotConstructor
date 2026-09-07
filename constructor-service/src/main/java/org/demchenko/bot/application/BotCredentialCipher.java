package org.demchenko.bot.application;

public interface BotCredentialCipher {
    String encrypt(String plaintext, String associatedData);
    String decrypt(String envelope, String associatedData);
}
