package org.demchenko.identity.data.repo;

import org.demchenko.identity.model.UserData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@Deprecated(forRemoval = false)
public interface UserRepository extends JpaRepository<UserData, Long> {
}
