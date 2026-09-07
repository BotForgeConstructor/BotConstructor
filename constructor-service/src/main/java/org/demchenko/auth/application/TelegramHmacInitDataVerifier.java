package org.demchenko.auth.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.demchenko.api.application.error.SafeApiException;
import org.demchenko.auth.config.TelegramAuthenticationProperties;
import org.demchenko.telegram.config.ManagementTelegramProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

@Component
@RequiredArgsConstructor
public class TelegramHmacInitDataVerifier implements TelegramInitDataVerifier {
    private static final int MAX_LENGTH = 8192;
    private final ManagementTelegramProperties managementProperties;
    private final TelegramAuthenticationProperties timing;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public AuthenticatedTelegramUser verify(String initData) {
        if (initData == null || initData.isBlank() || initData.length() > MAX_LENGTH) {
            throw malformed();
        }
        Map<String, String> values = parse(initData);
        String receivedHash = required(values, "hash");
        String authDateValue = required(values, "auth_date");
        String userJson = required(values, "user");

        TreeMap<String, String> signed = new TreeMap<>(values);
        signed.remove("hash");
        String dataCheckString = signed.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "\n" + right).orElse("");

        byte[] received;
        try {
            received = HexFormat.of().parseHex(receivedHash);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        byte[] expected = hmac(hmac("WebAppData".getBytes(StandardCharsets.UTF_8), tokenBytes()),
                dataCheckString.getBytes(StandardCharsets.UTF_8));
        if (!MessageDigest.isEqual(expected, received)) {
            throw invalid();
        }

        Instant authDate;
        try {
            authDate = Instant.ofEpochSecond(Long.parseLong(authDateValue));
        } catch (RuntimeException exception) {
            throw malformed();
        }
        Instant now = clock.instant();
        if (authDate.isBefore(now.minus(timing.maxInitDataAge()))) {
            throw failure("EXPIRED_TELEGRAM_INIT_DATA", "Telegram authentication data has expired");
        }
        if (authDate.isAfter(now.plus(timing.allowedFutureSkew()))) {
            throw failure("TELEGRAM_INIT_DATA_FROM_FUTURE", "Telegram authentication data has an invalid timestamp");
        }
        try {
            TelegramUserJson user = objectMapper.readValue(userJson, TelegramUserJson.class);
            if (user.id() <= 0 || user.first_name() == null || user.first_name().isBlank()) {
                throw malformed();
            }
            return new AuthenticatedTelegramUser(user.id(), user.username(), user.first_name(),
                    user.last_name(), user.language_code());
        } catch (SafeApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw malformed();
        }
    }

    private Map<String, String> parse(String input) {
        Map<String, String> result = new TreeMap<>();
        try {
            for (String pair : input.split("&", -1)) {
                int separator = pair.indexOf('=');
                if (separator <= 0) throw malformed();
                String key = decode(pair.substring(0, separator));
                String encodedValue = pair.substring(separator + 1);
                if ("user".equals(key) && (encodedValue.indexOf('{') >= 0 || encodedValue.indexOf('"') >= 0)) {
                    throw malformed();
                }
                String value = decode(encodedValue);
                if (result.putIfAbsent(key, value) != null) throw malformed();
            }
        } catch (SafeApiException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw malformed();
        }
        return result;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw malformed();
        return value;
    }

    private byte[] tokenBytes() {
        String token = managementProperties.token();
        if (token == null || token.isBlank()) throw invalid();
        return token.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] hmac(byte[] key, byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable");
        }
    }

    private SafeApiException malformed() { return failure("MALFORMED_TELEGRAM_INIT_DATA", "Telegram authentication data is malformed"); }
    private SafeApiException invalid() { return failure("INVALID_TELEGRAM_INIT_DATA", "Telegram authentication data is invalid"); }
    private SafeApiException failure(String code, String message) { return new SafeApiException(401, code, message); }

    private record TelegramUserJson(long id, String username, String first_name, String last_name, String language_code) { }
}
