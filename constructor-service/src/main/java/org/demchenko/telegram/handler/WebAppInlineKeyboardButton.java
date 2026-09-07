package org.demchenko.telegram.handler;

import org.telegram.telegrambots.meta.exceptions.TelegramApiValidationException;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Compatibility value used while the pinned Telegram client predates WebAppInfo. */
public final class WebAppInlineKeyboardButton extends InlineKeyboardButton {
    /* Field name intentionally matches Telegram's wire contract for Gson and Jackson. */
    @JsonProperty("web_app")
    private final WebAppInfo webApp;

    public WebAppInlineKeyboardButton(String text, String url) {
        super(text);
        this.webApp = new WebAppInfo(url);
    }

    @Override
    public void validate() throws TelegramApiValidationException {
        if (getText() == null || getText().isBlank() || webApp.url() == null || webApp.url().isBlank()) {
            throw new IllegalArgumentException("WebApp button requires text and URL");
        }
    }

    public WebAppInfo getWebApp() { return webApp; }

    public record WebAppInfo(String url) { }
}
