package org.demchenko.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticateWithTelegramUseCase {
    private final TelegramInitDataVerifier verifier;
    private final ObjectProvider<IdentityWorkspaceBootstrapService> bootstrapService;
    private final PlatformSessionIssuer sessionService;

    public AuthenticationResult authenticate(String initData) {
        IdentityWorkspaceBootstrapService bootstrap = bootstrapService.getIfAvailable();
        if (bootstrap == null) throw new IllegalStateException("Persistence is unavailable");
        AuthenticatedPlatformContext context = bootstrap.bootstrap(verifier.verify(initData));
        return new AuthenticationResult(context, sessionService.issue(context.userId()));
    }
}
