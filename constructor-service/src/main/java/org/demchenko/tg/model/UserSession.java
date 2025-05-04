package org.demchenko.tg.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.demchenko.tg.enums.LeadStatus;
import org.demchenko.tg.enums.ReachMethod;
import org.demchenko.tg.service.state.BotState;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSession {
    private Long userId;
    private BotState state;
    
    // Обов'язкові поля
    private String accountNumber;
    private String companyName;
    private Double creditLimit;
    private Double availableCredit;
    private LeadStatus leadStatus;
    private String contactName;
    private String phone;
    private String email;
    private ReachMethod bestWayToReach;
    private String workingHours;
    private String lastContactDate;
    
    // Опційні поля
    private String bestTimeToReach;
    private String bestInfoToReach;
    private String lastBookedDate;
    private String additionalContactInfo;
    private String comments;

    public UserSession(Long userId, BotState state) {
        this.userId = userId;
        this.state = state;
    }
}