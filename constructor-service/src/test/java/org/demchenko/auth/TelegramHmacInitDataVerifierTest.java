package org.demchenko.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.demchenko.api.application.error.SafeApiException;
import org.demchenko.auth.application.TelegramHmacInitDataVerifier;
import org.demchenko.auth.config.TelegramAuthenticationProperties;
import org.demchenko.telegram.config.ManagementTelegramProperties;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramHmacInitDataVerifierTest {
    private static final String TOKEN = "123456789:test_only_management_bot_token_value";
    private static final Instant NOW = Instant.ofEpochSecond(2_000_000_000L);
    private final TelegramHmacInitDataVerifier verifier = new TelegramHmacInitDataVerifier(
            new ManagementTelegramProperties(false, ManagementTelegramProperties.ConnectionMode.LONG_POLLING,
                    TOKEN, "test_management_bot"),
            new TelegramAuthenticationProperties(Duration.ofMinutes(5), Duration.ofSeconds(30)),
            new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void verifiesFrozenVectorComputedIndependently() {
        String user = "{\"id\":424242,\"first_name\":\"Test\",\"username\":\"tester\",\"language_code\":\"en\"}";
        String input = "auth_date=2000000000&query_id=frozen-query&user=" + encode(user)
                + "&hash=ebb03010f5a82ed9c75d2adb9fde6768bbdc7753aeba3759d57c300929101b53";

        var result = verifier.verify(input);

        assertThat(result.telegramUserId()).isEqualTo(424242L);
        assertThat(result.username()).isEqualTo("tester");
        assertThat(result.languageCode()).isEqualTo("en");
    }

    @Test
    void rejectsWrongHashAndTampering() {
        assertCode("auth_date=2000000000&user=" + encode("{\"id\":1,\"first_name\":\"X\"}")
                + "&hash=" + "00".repeat(32), "INVALID_TELEGRAM_INIT_DATA");
    }

    @Test
    void rejectsChangedQueryDateUserIdAndSignatureFromAnotherBot() {
        String user = "{\"id\":41,\"first_name\":\"Signed\"}";
        String signed = TestInitDataSigner.sign(TOKEN, Map.of(
                "auth_date", Long.toString(NOW.getEpochSecond()), "query_id", "original", "user", user));
        assertCode(signed.replace("query_id=original", "query_id=forged"), "INVALID_TELEGRAM_INIT_DATA");
        assertCode(signed.replace("auth_date=2000000000", "auth_date=1999999999"),
                "INVALID_TELEGRAM_INIT_DATA");
        assertCode(signed.replace(encode(user), encode("{\"id\":42,\"first_name\":\"Signed\"}")),
                "INVALID_TELEGRAM_INIT_DATA");
        assertCode(TestInitDataSigner.sign("987654321:another_fake_bot_token_value", NOW.getEpochSecond(), user),
                "INVALID_TELEGRAM_INIT_DATA");
    }

    @Test
    void rejectsMissingAndDuplicateCriticalFields() {
        assertCode("auth_date=2000000000&user=x", "MALFORMED_TELEGRAM_INIT_DATA");
        assertCode("hash=aa&auth_date=1&auth_date=2&user=x", "MALFORMED_TELEGRAM_INIT_DATA");
        assertCode("hash=aa&user=x", "MALFORMED_TELEGRAM_INIT_DATA");
        assertCode("hash=aa&auth_date=1", "MALFORMED_TELEGRAM_INIT_DATA");
    }

    @Test
    void rejectsMalformedEncodingAndOversizedInput() {
        assertCode("", "MALFORMED_TELEGRAM_INIT_DATA");
        assertCode("hash=%GG&auth_date=1&user=x", "MALFORMED_TELEGRAM_INIT_DATA");
        assertCode("x".repeat(8193), "MALFORMED_TELEGRAM_INIT_DATA");
    }

    @Test
    void rejectsUnencodedRawUserJsonButAcceptsSignedUnknownParameters() {
        String user = "{\"id\":7,\"first_name\":\"Raw\"}";
        String signed = TestInitDataSigner.sign(TOKEN, NOW.getEpochSecond(), user);
        assertCode(signed.replace(encode(user), user), "MALFORMED_TELEGRAM_INIT_DATA");

        String withUnknown = TestInitDataSigner.sign(TOKEN, Map.of(
                "auth_date", Long.toString(NOW.getEpochSecond()), "user", user, "future_field", "safe value"));
        assertThat(verifier.verify(withUnknown).telegramUserId()).isEqualTo(7L);
    }

    @Test
    void rejectsExpiredFutureAndMalformedUserAfterValidSignature() {
        assertCode(TestInitDataSigner.sign(TOKEN, 1_999_999_699L, "{\"id\":1,\"first_name\":\"X\"}"),
                "EXPIRED_TELEGRAM_INIT_DATA");
        assertCode(TestInitDataSigner.sign(TOKEN, 2_000_000_031L, "{\"id\":1,\"first_name\":\"X\"}"),
                "TELEGRAM_INIT_DATA_FROM_FUTURE");
        assertCode(TestInitDataSigner.sign(TOKEN, 2_000_000_000L, "not-json"),
                "MALFORMED_TELEGRAM_INIT_DATA");
    }

    @Test
    void acceptsAgeAndFutureSkewBoundariesAndUnicode() {
        assertThat(verifier.verify(TestInitDataSigner.sign(TOKEN, 1_999_999_700L,
                "{\"id\":2,\"first_name\":\"Тест\"}" )).firstName()).isEqualTo("Тест");
        assertThat(verifier.verify(TestInitDataSigner.sign(TOKEN, 2_000_000_030L,
                "{\"id\":3,\"first_name\":\"Future\"}" )).telegramUserId()).isEqualTo(3L);
    }

    private void assertCode(String input, String code) {
        assertThatThrownBy(() -> verifier.verify(input))
                .isInstanceOfSatisfying(SafeApiException.class, failure -> assertThat(failure.code()).isEqualTo(code));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
