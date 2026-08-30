package org.demchenko.identity.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;
import org.demchenko.identity.domain.Plan;

@Deprecated(forRemoval = false)
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
    @Enumerated(EnumType.ORDINAL)
    private Plan plan;
}
