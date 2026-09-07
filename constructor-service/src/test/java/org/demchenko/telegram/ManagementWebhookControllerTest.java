package org.demchenko.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.demchenko.api.application.error.SafeApiException;
import org.demchenko.telegram.config.ManagementWebhookProperties;
import org.demchenko.telegram.dispetcher.BotUpdateDispatcher;
import org.demchenko.telegram.web.ManagementWebhookController;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ManagementWebhookControllerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final BotUpdateDispatcher dispatcher = mock(BotUpdateDispatcher.class);
    private final ManagementWebhookController controller = new ManagementWebhookController(
            new ManagementWebhookProperties(true, "verification-secret", URI.create("/api/v1/telegram/management/webhook")),
            dispatcher, mapper);

    @Test
    void rejectsMissingAndWrongSecretBeforeDispatch() throws Exception {
        var body = mapper.readTree("{\"update_id\":1}");
        assertThatThrownBy(() -> controller.receive(null, body))
                .isInstanceOf(SafeApiException.class)
                .satisfies(error -> assertThat(((SafeApiException) error).status()).isEqualTo(401));
        assertThatThrownBy(() -> controller.receive("wrong", body))
                .isInstanceOf(SafeApiException.class)
                .satisfies(error -> assertThat(((SafeApiException) error).code()).isEqualTo("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(dispatcher);
    }

    @Test
    void dispatchesValidTelegramUpdateInProcess() throws Exception {
        var response = controller.receive("verification-secret", mapper.readTree("{\"update_id\":77}"));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(dispatcher).dispatch(update.capture());
        assertThat(update.getValue().getUpdateId()).isEqualTo(77);
    }
}
