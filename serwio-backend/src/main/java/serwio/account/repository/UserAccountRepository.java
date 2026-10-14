package serwio.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.account.model.UserAccount;
import java.util.UUID;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmail(String email);
}
