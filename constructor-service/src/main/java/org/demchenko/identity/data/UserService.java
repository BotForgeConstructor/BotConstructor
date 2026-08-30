package org.demchenko.identity.data;

import lombok.RequiredArgsConstructor;
import org.demchenko.identity.data.repo.UserRepository;
import org.demchenko.identity.domain.LegacyUserView;
import org.demchenko.identity.domain.Plan;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

@Service
@ConditionalOnBean(UserRepository.class)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public LegacyUserView findUserById(Long chatId) {
        return userRepository.findById(chatId)
                .map(user -> new LegacyUserView(user.getCountOfBots(), user.getPlan()))
                .orElseGet(() -> new LegacyUserView(0, Plan.FREE));
    }
}
