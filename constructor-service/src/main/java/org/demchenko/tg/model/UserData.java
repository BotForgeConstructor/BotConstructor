package org.demchenko.tg.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.demchenko.tg.enums.Plan;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_data")
public class UserData {

    @Id
    private Long chainId;
    private Integer countOfBots;
    private Plan plan;
    private List<String> botName;
}
