package org.demchenko.tg.data;

import lombok.RequiredArgsConstructor;
import org.demchenko.tg.data.repo.UserRepository;
import org.demchenko.tg.enums.Plan;
import org.demchenko.tg.model.UserData;
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
