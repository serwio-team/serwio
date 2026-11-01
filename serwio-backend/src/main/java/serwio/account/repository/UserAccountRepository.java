package serwio.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import serwio.account.model.UserAccount;
import java.util.UUID;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmail(String email);

    @Query("select a from UserAccount a where lower(trim(a.email)) = lower(trim(:email))")
    Optional<UserAccount> findByNormalizedEmail(String email);

    boolean existsByEmailIgnoreCase(String email);
}
