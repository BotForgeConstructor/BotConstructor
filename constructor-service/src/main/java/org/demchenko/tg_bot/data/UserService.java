package org.demchenko.tg_bot.data;

import lombok.RequiredArgsConstructor;
import org.demchenko.tg_bot.data.repo.UserRepository;
import org.demchenko.tg_bot.enums.Plan;
import org.demchenko.tg_bot.model.UserData;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserData findUserById(Long chatId) {
        return userRepository.findById(chatId)
                .orElseGet(() -> UserData.builder()
                        .chainId(chatId)
                        .countOfBots(0)
                        .plan(Plan.FREE)
                        .build());
    }
}
