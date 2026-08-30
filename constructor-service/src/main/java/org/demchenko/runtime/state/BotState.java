package org.demchenko.runtime.state;

import java.util.Arrays;
import java.util.Comparator;

public enum BotState {
    IDLE(0),               // РЅС–С‡РѕРіРѕ РЅРµ С‡РµРєР°С”РјРѕ
    WAITING_FOR_ACCOUNT_NUMBER(1),
    WAITING_FOR_COMPANY_NAME(2),
    WAITING_FOR_CREDIT_LIMIT(3),
    WAITING_FOR_AVAILABLE_CREDIT(4),
    WAITING_FOR_LEAD_STATUS(5),    // РєРЅРѕРїРєРё РґР»СЏ РІРёР±РѕСЂСѓ СЃС‚Р°С‚СѓСЃСѓ
    WAITING_FOR_CONTACT_NAME(6),
    WAITING_FOR_PHONE(7),
    WAITING_FOR_EMAIL(8),
    WAITING_FOR_BEST_WAY_TO_REACH(9),  // РєРЅРѕРїРєРё РґР»СЏ РІРёР±РѕСЂСѓ СЃРїРѕСЃРѕР±Сѓ Р·РІ'СЏР·РєСѓ
    WAITING_FOR_BEST_TIME_TO_REACH(10), // РѕРїС†С–Р№РЅРµ
    WAITING_FOR_BEST_INFO_TO_REACH(11), // РѕРїС†С–Р№РЅРµ
    WAITING_FOR_WORKING_HOURS(12),
    WAITING_FOR_LAST_CONTACT_DATE(13),
    WAITING_FOR_LAST_BOOKED_DATE(14),   // РѕРїС†С–Р№РЅРµ
    WAITING_FOR_ADDITIONAL_CONTACT_INFO(15), // РѕРїС†С–Р№РЅРµ
    WAITING_FOR_COMMENTS(16);  // РѕРїС†С–Р№РЅРµ

    private final int weight;

    BotState(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }

    public BotState getNextState() {
        return Arrays.stream(values())
                .filter(state -> state.weight > this.weight)
                .min(Comparator.comparingInt(BotState::getWeight))
                .orElse(IDLE);
    }

    public BotState getPreviousState() {
        return Arrays.stream(values())
                .filter(state -> state.weight < this.weight)
                .max(Comparator.comparingInt(BotState::getWeight))
                .orElse(IDLE);
    }
}