package org.demchenko.runtime.state;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSession {
    private Long userId;
    private BotState state;
    
    // РћР±РѕРІ'СЏР·РєРѕРІС– РїРѕР»СЏ
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
    
    // РћРїС†С–Р№РЅС– РїРѕР»СЏ
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
