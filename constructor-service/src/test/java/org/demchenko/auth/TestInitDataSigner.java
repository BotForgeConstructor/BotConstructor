package org.demchenko.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

public final class TestInitDataSigner {
    private TestInitDataSigner() { }

    public static String sign(String token, long authDate, String userJson) {
        return sign(token, Map.of("auth_date", Long.toString(authDate), "user", userJson));
    }

    public static String sign(String token, Map<String, String> values) {
        TreeMap<String, String> sorted = new TreeMap<>(values);
        String data = sorted.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "\n" + right).orElse("");
        byte[] secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
        String hash = HexFormat.of().formatHex(hmac(secret, data.getBytes(StandardCharsets.UTF_8)));
        return sorted.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .reduce((left, right) -> left + "&" + right).orElse("") + "&hash=" + hash;
    }

    private static byte[] hmac(byte[] key, byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
