package org.demchenko.tg.service.state;

import org.demchenko.tg.model.UserSession;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserSessionService {

    private final Map<Long, UserSession> sessions = new ConcurrentHashMap<>();

    public UserSession getSession(Long userId) {
        return sessions.computeIfAbsent(userId, id -> new UserSession(id, BotState.IDLE));
    }

    public void setSession(Long userId, UserSession session) {
        sessions.put(userId, session);
    }

    public void setState(Long userId, BotState state) {
        UserSession session = getSession(userId);
        session.setState(state);
    }

    public BotState getState(Long userId) {
        return getSession(userId).getState();
    }

    public void clearSession(Long userId) {
        sessions.remove(userId);
    }

    public UserSession getSessionByAccountNumber(String accountNumber) {
        return sessions.values().stream()
                .filter(session -> accountNumber.equals(session.getAccountNumber()))
                .findFirst()
                .orElse(null);
    }
}