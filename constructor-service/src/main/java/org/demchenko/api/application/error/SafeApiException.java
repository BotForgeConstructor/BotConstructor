package org.demchenko.api.application.error;

public class SafeApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final String clientMessage;

    public SafeApiException(int status, String code, String clientMessage) {
        super(code);
        this.status = status;
        this.code = code;
        this.clientMessage = clientMessage;
    }

    public int status() { return status; }
    public String code() { return code; }
    public String clientMessage() { return clientMessage; }
}
