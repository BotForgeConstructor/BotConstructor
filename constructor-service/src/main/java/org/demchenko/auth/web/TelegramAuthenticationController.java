package org.demchenko.auth.web;

import lombok.RequiredArgsConstructor;
import org.demchenko.api.generated.api.AuthApi;
import org.demchenko.api.generated.model.AuthResponse;
import org.demchenko.api.generated.model.TelegramAuthRequest;
import org.demchenko.auth.application.AuthenticateWithTelegramUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TelegramAuthenticationController implements AuthApi {
    private final AuthenticateWithTelegramUseCase useCase;
    private final AuthenticationMapper mapper;

    @Override
    public ResponseEntity<AuthResponse> exchangeTelegramSession(TelegramAuthRequest request) {
        return ResponseEntity.ok(mapper.toResponse(useCase.authenticate(request.getInitData())));
    }
}
