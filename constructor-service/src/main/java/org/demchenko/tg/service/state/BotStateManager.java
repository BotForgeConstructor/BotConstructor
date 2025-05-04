package org.demchenko.tg.service.state;

import lombok.RequiredArgsConstructor;
import org.demchenko.tg.model.UserSession;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BotStateManager {
    private final UserSessionService userSessionService;

    public void moveToNextState(Long userId) {
        UserSession session = userSessionService.getSession(userId);
        BotState nextState = session.getState().getNextState();
        session.setState(nextState);
        userSessionService.setSession(userId, session);
    }

    public void moveToPreviousState(Long userId) {
        UserSession session = userSessionService.getSession(userId);
        BotState previousState = session.getState().getPreviousState();
        session.setState(previousState);
        userSessionService.setSession(userId, session);
    }

    public void moveToSpecificState(Long userId, BotState targetState) {
        UserSession session = userSessionService.getSession(userId);
        session.setState(targetState);
        userSessionService.setSession(userId, session);
    }
} 